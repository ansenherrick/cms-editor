import crypto from "node:crypto"
import http from "node:http"
import { executeCms, CmsError } from "./cms.mjs"

export function createBridgeServer({ token, runWithSession, config }) {
  if (typeof token !== "string" || token.length < 32) throw new Error("FRAMER_BRIDGE_TOKEN must be at least 32 characters.")

  const expectedToken = hash(token)
  let tail = Promise.resolve()

  return http.createServer(async (request, response) => {
    response.setHeader("Cache-Control", "no-store")

    if (request.method === "GET" && request.url === "/health") {
      send(response, 200, { status: "ok" })
      return
    }

    if (request.method !== "POST" || request.url !== "/rpc") {
      send(response, 404, { problems: ["Not found."] })
      return
    }

    if (!isAuthorized(request.headers.authorization, expectedToken)) {
      send(response, 401, { problems: ["Framer bridge authorization failed."] })
      return
    }

    if (!String(request.headers["content-type"] ?? "").toLowerCase().startsWith("application/json")) {
      send(response, 415, { problems: ["Content-Type must be application/json."] })
      return
    }

    let body
    try {
      body = await readJsonBody(request)
    } catch (error) {
      send(response, error.status ?? 400, { problems: [error.message] })
      return
    }

    tail = tail.then(() => runRpc(body, response, runWithSession, config), () => runRpc(body, response, runWithSession, config))
    await tail.catch(() => {})
  })
}

async function runRpc(body, response, runWithSession, config) {
  try {
    const result = await runWithSession((framer) => executeCms(framer, config, body))
    if (!response.destroyed) send(response, 200, result)
  } catch (error) {
    if (response.destroyed) return
    const mapped = mapError(error)
    logBridgeError(error, mapped.status)
    send(response, mapped.status, { problems: [mapped.message] })
  }
}

function mapError(error) {
  if (error instanceof CmsError) return { status: error.status, message: error.message }
  if (error?.code === "UNAUTHORIZED") return { status: 503, message: "Framer rejected the configured API key." }
  return { status: 502, message: "Framer request failed. Check the bridge logs for details." }
}

function logBridgeError(error, status) {
  if (error instanceof CmsError) return
  const message = String(error?.message ?? "Unknown error")
    .replace(/[A-Za-z0-9_-]{24,}/g, "[redacted]")
  console.error(JSON.stringify({
    level: "error",
    status,
    name: error?.name ?? "Error",
    code: error?.code,
    message,
  }))
}

async function readJsonBody(request) {
  let raw = ""
  for await (const chunk of request) {
    raw += chunk
    if (Buffer.byteLength(raw) > 1024 * 1024) throw new CmsError(413, "Request body is too large.")
  }

  try {
    return JSON.parse(raw)
  } catch {
    throw new CmsError(400, "Request body must be valid JSON.")
  }
}

function isAuthorized(header, expectedToken) {
  const prefix = "Bearer "
  if (!header?.startsWith(prefix)) return false
  const provided = hash(header.slice(prefix.length))
  return crypto.timingSafeEqual(provided, expectedToken)
}

function hash(value) {
  return crypto.createHash("sha256").update(value).digest()
}

function send(response, status, payload) {
  response.statusCode = status
  response.setHeader("Content-Type", "application/json")
  response.end(JSON.stringify(payload))
}
