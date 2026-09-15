const { onDocumentCreated, onDocumentUpdated, onDocumentDeleted } = require('firebase-functions/v2/firestore');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const admin = require('firebase-admin');

admin.initializeApp();
const db = admin.firestore();

async function getTokens(uid) {
  if (!uid) return [];
  const snap = await db.collection('users').doc(uid).collection('devices').get();
  return snap.docs.map(d => d.get('token')).filter(Boolean);
}

async function saveNotification(uid, payload) {
  if (!uid) return null;
  const ref = db.collection('notifications').doc();
  await ref.set({
    recipientUid: uid,
    title: payload.title || 'Yap!',
    body: payload.body || '',
    type: payload.type || 'general',
    orgId: payload.orgId || '',
    taskId: payload.taskId || '',
    inviteId: payload.inviteId || '',
    actorUid: payload.actorUid || '',
    read: false,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    data: payload.data || {}
  });
  return ref.id;
}

async function notifyUser(uid, payload) {
  const notificationId = await saveNotification(uid, payload);
  const tokens = await getTokens(uid);
  if (!tokens.length) return;
  const data = {
    type: String(payload.type || 'general'),
    orgId: String(payload.orgId || ''),
    taskId: String(payload.taskId || ''),
    inviteId: String(payload.inviteId || ''),
    notificationId: String(notificationId || '')
  };
  const response = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title: payload.title || 'Yap!', body: payload.body || '' },
    data,
    android: { priority: 'high', notification: { channelId: 'yap_general' } }
  });
  const invalid = [];
  response.responses.forEach((r, i) => {
    if (!r.success && ['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'].includes(r.error?.code)) invalid.push(tokens[i]);
  });
  if (invalid.length) {
    const devices = await db.collection('users').doc(uid).collection('devices').get();
    const batch = db.batch();
    devices.docs.forEach(d => { if (invalid.includes(d.get('token'))) batch.delete(d.ref); });
    await batch.commit();
  }
}

async function managerUids(orgId) {
  const snap = await db.collection('orgs').doc(orgId).collection('members').get();
  return snap.docs.filter(d => ['owner', 'admin'].includes(d.get('level'))).map(d => d.id);
}

function dateText(v) {
  return typeof v === 'string' ? v : '';
}

exports.onTaskCreated = onDocumentCreated('orgs/{orgId}/tasks/{taskId}', async event => {
  const data = event.data?.data(); if (!data) return;
  const { orgId, taskId } = event.params;
  if (data.assigneeUid) {
    await notifyUser(data.assigneeUid, {
      type: data.priority === 'high' ? 'urgent_task' : 'task_assigned',
      title: data.priority === 'high' ? 'Acil görev atandı' : 'Yeni görev atandı',
      body: data.title || 'Yeni görevin var.', orgId, taskId, actorUid: data.createdBy || ''
    });
  }
  if (!data.assigneeUid) {
    for (const uid of await managerUids(orgId)) await notifyUser(uid, { type: 'unassigned_task', title: 'Atanmamış görev', body: `${data.title || 'Bir görev'} henüz kimseye atanmadı.`, orgId, taskId });
  }
});

exports.onTaskUpdated = onDocumentUpdated('orgs/{orgId}/tasks/{taskId}', async event => {
  const before = event.data?.before.data(); const after = event.data?.after.data(); if (!before || !after) return;
  const { orgId, taskId } = event.params;
  const target = after.assigneeUid;
  if (before.assigneeUid !== after.assigneeUid) {
    if (before.assigneeUid) await notifyUser(before.assigneeUid, { type: 'task_removed', title: 'Görev senden alındı', body: after.title || before.title || 'Bir görev başka kişiye devredildi.', orgId, taskId });
    if (after.assigneeUid) await notifyUser(after.assigneeUid, { type: 'task_transferred', title: 'Görev sana devredildi', body: after.title || 'Yeni bir görev sana atandı.', orgId, taskId });
  }
  if (target && before.end !== after.end) await notifyUser(target, { type: 'task_date_changed', title: 'Görev tarihi değişti', body: `${after.title || 'Görev'} için yeni bitiş tarihi: ${dateText(after.end) || 'belirtilmedi'}.`, orgId, taskId });
  if (target && before.priority !== 'high' && after.priority === 'high') await notifyUser(target, { type: 'urgent_task', title: 'Görev acil olarak işaretlendi', body: after.title || 'Görevin önceliği yükseltildi.', orgId, taskId });
  if (target && JSON.stringify(before) !== JSON.stringify(after) && before.assigneeUid === after.assigneeUid && before.end === after.end && before.priority === after.priority) {
    await notifyUser(target, { type: 'task_updated', title: 'Görev güncellendi', body: after.title || 'Görev bilgilerinde değişiklik yapıldı.', orgId, taskId });
  }
  if (before.status !== after.status) {
    if (after.status === 'done' || after.status === 'completed') {
      for (const uid of await managerUids(orgId)) await notifyUser(uid, { type: 'task_completed', title: 'Görev tamamlandı', body: after.title || 'Bir görev tamamlandı.', orgId, taskId, actorUid: target || '' });
    }
    if ((before.status === 'done' || before.status === 'completed') && !['done', 'completed'].includes(after.status) && target) {
      await notifyUser(target, { type: 'task_reopened', title: 'Görev yeniden açıldı', body: after.title || 'Tamamlanan görev yeniden açıldı.', orgId, taskId });
    }
  }
});

exports.onMessageCreated = onDocumentCreated('orgs/{orgId}/tasks/{taskId}/messages/{messageId}', async event => {
  const msg = event.data?.data(); if (!msg) return;
  const { orgId, taskId } = event.params;
  const task = (await db.collection('orgs').doc(orgId).collection('tasks').doc(taskId).get()).data() || {};
  const recipients = new Set();
  if (task.assigneeUid && task.assigneeUid !== msg.senderUid) recipients.add(task.assigneeUid);
  for (const uid of await managerUids(orgId)) if (uid !== msg.senderUid) recipients.add(uid);
  for (const uid of recipients) await notifyUser(uid, { type: String(msg.text || '').includes('@') ? 'mention' : 'new_message', title: String(msg.text || '').includes('@') ? 'Sohbette senden bahsedildi' : 'Yeni görev mesajı', body: `${msg.senderName || 'Ekip'}: ${String(msg.text || '').slice(0, 180)}`, orgId, taskId, actorUid: msg.senderUid || '' });
});

exports.onMemberCreated = onDocumentCreated('orgs/{orgId}/members/{uid}', async event => {
  const member = event.data?.data(); if (!member) return;
  const { orgId, uid } = event.params;
  for (const managerUid of await managerUids(orgId)) if (managerUid !== uid) await notifyUser(managerUid, { type: 'member_joined', title: 'Yeni ekip üyesi', body: `${member.name || member.email || 'Yeni kullanıcı'} ekibe katıldı.`, orgId, actorUid: uid });
});

exports.onMemberUpdated = onDocumentUpdated('orgs/{orgId}/members/{uid}', async event => {
  const before = event.data?.before.data(); const after = event.data?.after.data(); if (!before || !after) return;
  const { orgId, uid } = event.params;
  if (before.role !== after.role) await notifyUser(uid, { type: 'role_changed', title: 'Rolün değiştirildi', body: `Yeni rolün: ${after.role || 'Çalışan'}`, orgId });
  if (before.level !== after.level) await notifyUser(uid, { type: 'permission_changed', title: 'Yetkin değiştirildi', body: after.level === 'admin' ? 'Yönetici yetkisi verildi.' : 'Yönetici yetkin güncellendi.', orgId });
});

exports.onMemberDeleted = onDocumentDeleted('orgs/{orgId}/members/{uid}', async event => {
  const member = event.data?.data() || {};
  const { orgId, uid } = event.params;
  await notifyUser(uid, { type: 'removed_from_team', title: 'Ekip üyeliğin sona erdi', body: `${member.name || 'Hesabın'} şirket ekibinden çıkarıldı.`, orgId });
  for (const managerUid of await managerUids(orgId)) await notifyUser(managerUid, { type: 'member_left', title: 'Ekip üyesi ayrıldı', body: `${member.name || member.email || 'Bir kullanıcı'} artık ekipte değil.`, orgId });
});

exports.onInviteUpdated = onDocumentUpdated('invites/{inviteId}', async event => {
  const before = event.data?.before.data(); const after = event.data?.after.data(); if (!before || !after) return;
  if (before.status === 'pending' && after.status === 'accepted' && after.orgId) {
    for (const uid of await managerUids(after.orgId)) await notifyUser(uid, { type: 'invite_accepted', title: 'Davet kabul edildi', body: `${after.email || 'Davet edilen kişi'} ekibe katıldı.`, orgId: after.orgId, inviteId: event.params.inviteId, actorUid: after.acceptedBy || '' });
  }
});

exports.dailyTaskReminders = onSchedule({ schedule: '0 8 * * *', timeZone: 'Europe/Istanbul' }, async () => {
  const orgs = await db.collection('orgs').get();
  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Istanbul' });
  const tomorrowDate = new Date(Date.now() + 86400000);
  const tomorrow = tomorrowDate.toLocaleDateString('en-CA', { timeZone: 'Europe/Istanbul' });
  for (const org of orgs.docs) {
    const tasks = await org.ref.collection('tasks').get();
    const summary = new Map();
    for (const doc of tasks.docs) {
      const t = doc.data(); if (!t.assigneeUid) continue;
      const status = t.status || 'open'; if (['done', 'completed'].includes(status)) continue;
      if (!summary.has(t.assigneeUid)) summary.set(t.assigneeUid, { total: 0, urgent: 0 });
      if (t.start === today || t.end === today) { const s = summary.get(t.assigneeUid); s.total++; if (t.priority === 'high') s.urgent++; }
      if (t.start === today) await notifyUser(t.assigneeUid, { type: 'task_starts_today', title: 'Görev bugün başlıyor', body: t.title || 'Bugün başlayan bir görevin var.', orgId: org.id, taskId: doc.id });
      if (t.end === tomorrow) await notifyUser(t.assigneeUid, { type: 'deadline_soon', title: 'Bitiş tarihi yaklaşıyor', body: `${t.title || 'Görev'} yarın bitiyor.`, orgId: org.id, taskId: doc.id });
      if (t.end && t.end < today) {
        await notifyUser(t.assigneeUid, { type: 'task_overdue', title: 'Görev gecikti', body: `${t.title || 'Görev'} bitiş tarihini geçti.`, orgId: org.id, taskId: doc.id });
        for (const uid of await managerUids(org.id)) await notifyUser(uid, { type: 'task_overdue_manager', title: 'Geciken görev var', body: `${t.title || 'Bir görev'} gecikti.`, orgId: org.id, taskId: doc.id });
      }
    }
    for (const [uid, s] of summary.entries()) await notifyUser(uid, { type: 'daily_summary', title: 'Bugünkü programın', body: `Bugün ${s.total} görevin var${s.urgent ? `, ${s.urgent} tanesi acil` : ''}.`, orgId: org.id });
  }
});

exports.weeklyManagerSummary = onSchedule({ schedule: '0 9 * * 1', timeZone: 'Europe/Istanbul' }, async () => {
  const orgs = await db.collection('orgs').get();
  for (const org of orgs.docs) {
    const tasks = await org.ref.collection('tasks').get();
    let open = 0, done = 0, urgent = 0, unassigned = 0;
    tasks.docs.forEach(d => { const t = d.data(); if (['done', 'completed'].includes(t.status)) done++; else open++; if (t.priority === 'high' && !['done','completed'].includes(t.status)) urgent++; if (!t.assigneeUid) unassigned++; });
    for (const uid of await managerUids(org.id)) await notifyUser(uid, { type: 'weekly_summary', title: 'Haftalık Yap! özeti', body: `${open} açık, ${done} tamamlanmış, ${urgent} acil ve ${unassigned} atanmamış görev bulunuyor.`, orgId: org.id });
  }
});
