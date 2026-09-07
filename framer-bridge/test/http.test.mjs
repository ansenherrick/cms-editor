import assert from "node:assert/strict"
import test from "node:test"
import { once } from "node:events"
import { createBridgeServer } from "../src/http.mjs"
import { CmsError } from "../src/cms.mjs"

const token = "abcdefghijklmnopqrstuvwxyz0123456789"

async function withServer(runWithSession) {
  const server = createBridgeServer({
    token,
    runWithSession,
    config: { websiteId: "personal-site", collection: "blog-posts" },
  })
  server.listen(0, "127.0.0.1")
  await once(server, "listening")
  const { port } = server.address()
  return {
    url: `http://127.0.0.1:${port}/rpc`,
    close: async () => {
      server.closeAllConnections()
      server.close()
      await once(server, "close")
    },
  }
}

async function post(url, body, headers = {}) {
  return fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...headers,
    },
    body,
  })
}

test("rejects unauthorized requests before opening a Framer session", async () => {
  let called = false
  const server = await withServer(async () => {
    called = true
  })

  try {
    const response = await post(server.url, JSON.stringify({ operation: "list", websiteId: "personal-site" }), {
      Authorization: "Bearer wrong",
    })
    assert.equal(response.status, 401)
    assert.equal(called, false)
  } finally {
    await server.close()
  }
})

test("maps controlled provider errors to JSON API errors", async () => {
  const server = await withServer(async () => {
    throw new CmsError(409, "Schema mismatch.")
  })

  try {
    const response = await post(server.url, JSON.stringify({ operation: "list", websiteId: "personal-site" }))
    assert.equal(response.status, 409)
    assert.deepEqual(await response.json(), { problems: ["Schema mismatch."] })
  } finally {
    await server.close()
  }
})

test("logs controlled server errors", async () => {
  const originalError = console.error
  const logged = []
  console.error = (...args) => logged.push(args.join(" "))
  const server = await withServer(async () => {
    throw new CmsError(502, "Framer accepted the create request, but the new post was not returned.")
  })

  try {
    const response = await post(server.url, JSON.stringify({ operation: "create", websiteId: "personal-site" }))
    assert.equal(response.status, 502)
    assert.equal(logged.length, 1)
    assert.match(logged[0], /new post was not returned/)
  } finally {
    console.error = originalError
    await server.close()
  }
})

test("redacts unexpected SDK failures", async () => {
  const server = await withServer(async () => {
    throw new Error("secret stack with token")
  })

  try {
    const response = await post(server.url, JSON.stringify({ operation: "list", websiteId: "personal-site" }))
    assert.equal(response.status, 502)
    assert.deepEqual(await response.json(), { problems: ["Framer request failed. Check the bridge logs for details."] })
  } finally {
    await server.close()
  }
})

test("rejects invalid JSON", async () => {
  const server = await withServer(async () => ({ ok: true }))

  try {
    const response = await post(server.url, "{")
    assert.equal(response.status, 400)
  } finally {
    await server.close()
  }
})

test("serializes RPC writes through one session runner", async () => {
  const order = []
  const server = await withServer(async () => {
    order.push("start")
    await new Promise((resolve) => setTimeout(resolve, 20))
    order.push("end")
    return { ok: true }
  })

  try {
    await Promise.all([
      post(server.url, JSON.stringify({ operation: "list", websiteId: "personal-site" })),
      post(server.url, JSON.stringify({ operation: "list", websiteId: "personal-site" })),
    ])
    assert.deepEqual(order, ["start", "end", "start", "end"])
  } finally {
    await server.close()
  }
})
