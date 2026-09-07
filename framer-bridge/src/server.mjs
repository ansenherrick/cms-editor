import { connect } from "framer-api"
import { createBridgeServer } from "./http.mjs"
import { createSessionRunner } from "./session.mjs"

const required = ["FRAMER_API_KEY", "FRAMER_PROJECT_URL", "FRAMER_COLLECTION", "FRAMER_BRIDGE_TOKEN", "CMS_WEBSITE_ID"]
for (const name of required) {
  if (!process.env[name]) {
    console.error(`${name} is required.`)
    process.exit(1)
  }
}

const runWithSession = createSessionRunner(connect, process.env.FRAMER_PROJECT_URL, process.env.FRAMER_API_KEY)
const server = createBridgeServer({
  token: process.env.FRAMER_BRIDGE_TOKEN,
  runWithSession,
  config: {
    project: process.env.FRAMER_PROJECT_URL,
    collection: process.env.FRAMER_COLLECTION,
    websiteId: process.env.CMS_WEBSITE_ID,
  },
})

server.requestTimeout = 15_000
server.headersTimeout = 10_000

server.listen(8090, "0.0.0.0", () => {
  console.log("Private Framer bridge listening on 8090.")
})

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    server.close(() => process.exit(0))
    setTimeout(() => process.exit(0), 5_000).unref()
  })
}
