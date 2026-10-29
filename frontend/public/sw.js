self.addEventListener('push', (event) => {
  let payload = {
    title: 'HaulMaster dispatch update',
    body: 'There is a new operational update.',
    path: 'app',
  }

  if (event.data) {
    try {
      payload = { ...payload, ...event.data.json() }
    } catch (error) {
      console.error('Could not read the push notification payload.', error)
    }
  }

  event.waitUntil(self.registration.showNotification(payload.title, {
    body: payload.body,
    icon: `${self.registration.scope}app-icon.svg`,
    badge: `${self.registration.scope}app-icon.svg`,
    data: { path: payload.path },
  }))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const path = event.notification.data?.path
  const targetUrl = new URL(typeof path === 'string' && path.startsWith('/') ? path : 'app', self.registration.scope)
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const client of windows) {
      if (new URL(client.url).origin === targetUrl.origin && 'focus' in client) {
        await client.navigate(targetUrl.href)
        return client.focus()
      }
    }
    return self.clients.openWindow(targetUrl.href)
  })())
})
