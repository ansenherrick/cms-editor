import { CmsError } from "./cms.mjs"

export function createSessionRunner(connect, projectUrlOrId, apiKey, timeoutMs = 45_000) {
  return async function runWithSession(operation) {
    let framer = null
    let expired = false
    let timer = null

    const work = (async () => {
      framer = await connect(projectUrlOrId, apiKey)
      if (expired) {
        await disconnectQuietly(framer)
        return null
      }
      return operation(framer)
    })()

    const deadline = new Promise((_, reject) => {
      timer = setTimeout(() => {
        expired = true
        reject(new CmsError(504, "Framer did not respond in time. Reload the app and check whether the last write reached Framer before retrying."))
      }, timeoutMs)
    })

    try {
      return await Promise.race([work, deadline])
    } finally {
      if (timer) clearTimeout(timer)
      if (framer && !expired) await disconnectQuietly(framer)
    }
  }
}

async function disconnectQuietly(framer) {
  try {
    await Promise.race([
      framer.disconnect(),
      new Promise((resolve) => setTimeout(resolve, 2_000)),
    ])
  } catch {
  }
}
