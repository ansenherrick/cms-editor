const endpoint = "http://127.0.0.1:8090/rpc"
const required = ["FRAMER_BRIDGE_TOKEN", "CMS_WEBSITE_ID"]
for (const name of required) {
  if (!process.env[name]) {
    console.error(`${name} is required.`)
    process.exit(1)
  }
}

const controller = new AbortController()
const timer = setTimeout(() => controller.abort(), 55_000)

try {
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${process.env.FRAMER_BRIDGE_TOKEN}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ operation: "inspect", websiteId: process.env.CMS_WEBSITE_ID }),
    signal: controller.signal,
  })

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    console.error(JSON.stringify(payload ?? { problems: ["Framer check failed."] }, null, 2))
    process.exit(1)
  }

  console.log(JSON.stringify(payload, null, 2))
} catch (error) {
  console.error(error.name === "AbortError" ? "Framer check timed out." : "Framer check failed.")
  process.exit(1)
} finally {
  clearTimeout(timer)
}
