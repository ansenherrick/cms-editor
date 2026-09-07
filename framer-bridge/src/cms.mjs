import crypto from "node:crypto"

export class CmsError extends Error {
  constructor(status, message) {
    super(message)
    this.name = "CmsError"
    this.status = status
  }
}

const requiredFieldSpecs = [
  { key: "title", names: ["Title", "title"], type: "string" },
  { key: "date", names: ["Date", "date"], type: "date" },
  { key: "bodyText", names: ["Body Text", "Body", "bodyText", "body-text"], type: "formattedText" },
]

const optionalFieldSpecs = [
  { key: "subheading", names: ["Sub Text", "Subheading", "subheading"], type: "formattedText", empty: "" },
  { key: "imageUrl", names: ["Image Link", "Image URL", "imageUrl", "image-url"], type: ["link", "string"], empty: null },
  { key: "imageAltText", names: ["Image Alt Text", "Image Alt", "imageAltText"], type: "string", empty: "" },
  { key: "imageSize", names: ["Image Size", "imageSize"], type: ["enum", "string"], empty: "" },
  { key: "link", names: ["Optional link", "Link", "link"], type: ["link", "string"], empty: null },
  { key: "linkText", names: ["Link text", "Link Text", "linkText"], type: "string", empty: "" },
]

const fieldSpecs = [...requiredFieldSpecs, ...optionalFieldSpecs]
const fieldSpecByKey = new Map(fieldSpecs.map((spec) => [spec.key, spec]))

export async function executeCms(framer, config, request) {
  if (request.websiteId !== config.websiteId) {
    throw new CmsError(403, `This Framer provider is configured for website '${config.websiteId}'.`)
  }

  if (!["inspect", "list", "create", "update", "delete", "setPublished"].includes(request.operation)) {
    throw new CmsError(400, "Unsupported CMS operation.")
  }

  const context = await loadContext(framer, config)
  switch (request.operation) {
    case "inspect":
      return inspectContext(context)
    case "list":
      return listPosts(context)
    case "create":
      return createPost(context, request.draft)
    case "update":
      return updatePost(context, request.postId, request.draft)
    case "delete":
      return deletePost(context, request.postId)
    case "setPublished":
      return setPublished(framer, context, request.postId, request.isPublished, config.autoDeploy === true)
  }
}

async function loadContext(framer, config) {
  const collections = await framer.getCollections()
  const collection = resolveCollection(collections, config.collection)
  if (collection.managedBy !== "user" || collection.readonly) {
    throw new CmsError(409, `Collection '${collection.name}' is not editable by this API key.`)
  }

  const fields = await collection.getFields()
  return {
    collection,
    fieldMap: resolveFields(fields),
  }
}

function resolveCollection(collections, configured) {
  const exactId = collections.find((collection) => collection.id === configured)
  if (exactId) return exactId

  const normalized = normalizeName(configured)
  const matches = collections.filter((collection) => normalizeName(collection.name) === normalized)
  if (matches.length === 1) return matches[0]
  if (matches.length > 1) {
    throw new CmsError(409, `More than one Framer collection matches '${configured}'. Set CMS_FRAMER_COLLECTION to the collection id.`)
  }

  throw new CmsError(404, `Framer collection '${configured}' was not found.`)
}

function resolveFields(fields) {
  const fieldMap = new Map()
  for (const spec of fieldSpecs) {
    const candidates = fields.filter((field) => spec.names.some((name) => normalizeName(name) === normalizeName(field.name)))
    if (candidates.length > 1) {
      throw new CmsError(409, `More than one Framer field matches '${spec.names[0]}'.`)
    }
    if (candidates.length === 0) {
      if (requiredFieldSpecs.includes(spec)) {
        throw new CmsError(409, `Framer collection is missing required field '${spec.names[0]}'.`)
      }
      continue
    }

    const field = candidates[0]
    const allowedTypes = Array.isArray(spec.type) ? spec.type : [spec.type]
    if (!allowedTypes.includes(field.type)) {
      throw new CmsError(409, `Framer field '${field.name}' must be ${allowedTypes.join(" or ")}, but it is ${field.type}.`)
    }
    fieldMap.set(spec.key, field)
  }
  return fieldMap
}

function inspectContext(context) {
  const fields = {}
  for (const spec of fieldSpecs) {
    const field = context.fieldMap.get(spec.key)
    fields[spec.key] = field ? { id: field.id, name: field.name, type: field.type } : null
  }
  return {
    collection: {
      id: context.collection.id,
      name: context.collection.name,
      managedBy: context.collection.managedBy,
      readonly: context.collection.readonly,
    },
    fields,
  }
}

async function listPosts(context) {
  const items = await context.collection.getItems()
  return items
    .map((item) => toPost(context, item))
    .sort((left, right) => right.date.localeCompare(left.date))
}

async function createPost(context, draft) {
  validateDraft(draft)
  await rejectUnknownRequiredFields(context)
  const slug = `${slugify(draft.title)}-${crypto.randomUUID().slice(0, 8)}`
  await context.collection.addItems([{ slug, draft: true, fieldData: toFieldData(context, draft, { includeEmptyOptionals: false }) }])
  const created = await findCreatedItem(context, slug)
  if (!created) throw new CmsError(502, "Framer accepted the create request, but the new post was not returned.")
  return toPost(context, created)
}

async function findCreatedItem(context, slug) {
  for (let attempt = 0; attempt < 10; attempt++) {
    const created = (await context.collection.getItems()).find((item) => item.slug === slug)
    if (created) return created
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  return null
}

async function updatePost(context, postId, draft) {
  validatePostId(postId)
  validateDraft(draft)
  const item = await findItem(context, postId)
  const updated = await item.setAttributes({ fieldData: toFieldData(context, draft) })
  if (!updated) throw new CmsError(404, `Post '${postId}' was not found.`)
  return toPost(context, updated)
}

async function deletePost(context, postId) {
  validatePostId(postId)
  const item = await findItem(context, postId)
  await item.remove()
  return { ok: true }
}

async function setPublished(framer, context, postId, isPublished, autoDeploy) {
  validatePostId(postId)
  if (typeof isPublished !== "boolean") throw new CmsError(400, "isPublished is required.")
  if (isPublished && autoDeploy) await requireDeploymentPermissions(framer)
  const item = await findItem(context, postId)
  const updated = await item.setAttributes({ draft: !isPublished })
  if (!updated) throw new CmsError(404, `Post '${postId}' was not found.`)
  if (isPublished && autoDeploy) await publishToProduction(framer)
  return toPost(context, updated)
}

async function requireDeploymentPermissions(framer) {
  const [canPublish, canDeploy] = await Promise.all([
    framer.isAllowedTo("publish"),
    framer.isAllowedTo("deploy"),
  ])
  if (!canPublish || !canDeploy) {
    throw new CmsError(403, "The configured Framer API key is not allowed to publish and deploy this project.")
  }
}

async function publishToProduction(framer) {
  const { deployment } = await framer.publish()
  if (!deployment?.id) throw new CmsError(502, "Framer did not return a deployment after publishing.")
  await framer.deploy(deployment.id)
}

async function findItem(context, postId) {
  const item = (await context.collection.getItems()).find((candidate) => candidate.id === postId)
  if (!item) throw new CmsError(404, `Post '${postId}' was not found.`)
  return item
}

async function rejectUnknownRequiredFields(context) {
  const mappedFieldIds = new Set([...context.fieldMap.values()].map((field) => field.id))
  const fields = await context.collection.getFields()
  const unknownRequired = fields.find((field) => field.required && !mappedFieldIds.has(field.id))
  if (unknownRequired) {
    throw new CmsError(409, `Framer field '${unknownRequired.name}' is required but is not mapped by the CMS editor.`)
  }
}

function toPost(context, item) {
  return {
    id: item.id,
    date: readField(context, item, "date")?.slice(0, 10) ?? "",
    title: readField(context, item, "title") ?? "",
    imageUrl: emptyToNull(readField(context, item, "imageUrl")),
    imageAltText: emptyToNull(readField(context, item, "imageAltText")),
    imageSize: normalizeImageSize(readField(context, item, "imageSize")),
    subheading: emptyToNull(readField(context, item, "subheading")),
    link: emptyToNull(readField(context, item, "link")),
    linkText: emptyToNull(readField(context, item, "linkText")),
    bodyText: readField(context, item, "bodyText") ?? "",
    isPublished: item.draft === false,
  }
}

function readField(context, item, key) {
  const field = context.fieldMap.get(key)
  if (!field) return null
  const value = item.fieldData?.[field.id]
  if (value == null) return null
  if (field.type === "enum") {
    const selected = field.cases?.find((fieldCase) => fieldCase.id === value.value)
    return selected?.name ?? value.value ?? null
  }
  return value.value ?? null
}

function toFieldData(context, draft, options = { includeEmptyOptionals: true }) {
  const fieldData = {}
  for (const [key, spec] of fieldSpecByKey.entries()) {
    const field = context.fieldMap.get(key)
    const value = draft[key] ?? null
    const isRequired = requiredFieldSpecs.includes(spec)
    if (!isRequired && !options.includeEmptyOptionals && (value === null || value === "")) {
      continue
    }
    if (!field) {
      if (value !== null && value !== "") throw new CmsError(409, `Framer collection is missing optional field '${spec.names[0]}'.`)
      continue
    }

    fieldData[field.id] = encodeFieldValue(field, spec, value)
  }
  return fieldData
}

function encodeFieldValue(field, spec, value) {
  if (field.type === "formattedText") return { type: "formattedText", value: value ?? "", contentType: "markdown" }
  if (field.type === "date") return { type: "date", value: `${value}T00:00:00.000Z` }
  if (field.type === "link") return { type: "link", value: value || null }
  if (field.type === "enum") return { type: "enum", value: value ? findEnumCaseId(field, value) : "" }
  return { type: "string", value: value ?? spec.empty ?? "" }
}

function findEnumCaseId(field, value) {
  const normalized = normalizeName(value)
  const match = field.cases?.find((fieldCase) => normalizeName(fieldCase.name) === normalized || normalizeName(fieldCase.id) === normalized)
  if (!match) throw new CmsError(422, `Image Size must match one of the Framer enum cases.`)
  return match.id
}

function validateDraft(draft) {
  if (!draft || typeof draft !== "object" || Array.isArray(draft)) throw new CmsError(400, "draft is required.")
  for (const key of Object.keys(draft)) {
    if (!fieldSpecByKey.has(key)) throw new CmsError(400, `Unknown draft field '${key}'.`)
  }

  const problems = []
  requireString(draft, "date", problems)
  requireString(draft, "title", problems)
  requireString(draft, "bodyText", problems)
  for (const key of ["imageUrl", "imageAltText", "imageSize", "subheading", "link", "linkText"]) {
    if (draft[key] !== undefined && draft[key] !== null && typeof draft[key] !== "string") problems.push(`${key} must be a string.`)
  }

  if (typeof draft.title === "string" && draft.title.trim().length === 0) problems.push("Title is required.")
  if (typeof draft.title === "string" && draft.title.length > 160) problems.push("Title must be 160 characters or fewer.")
  if (typeof draft.bodyText === "string" && draft.bodyText.trim().length === 0) problems.push("Body text is required.")
  if (typeof draft.bodyText === "string" && draft.bodyText.length > 50_000) problems.push("Body text must be 50,000 characters or fewer.")
  if (typeof draft.date === "string" && !isIsoDate(draft.date)) problems.push("Date must be in YYYY-MM-DD format.")
  if (draft.imageUrl && !isHttpUrl(draft.imageUrl)) problems.push("Image URL must start with http:// or https://.")
  if (draft.link && !isHttpUrl(draft.link)) problems.push("Link must start with http:// or https://.")
  if (Boolean(draft.imageUrl) !== Boolean(draft.imageAltText) || Boolean(draft.imageUrl) !== Boolean(draft.imageSize)) {
    problems.push("Image URL, alt text, and size must be provided together.")
  }
  if (Boolean(draft.link) !== Boolean(draft.linkText)) problems.push("Link and link text must be provided together.")

  if (problems.length > 0) throw new CmsError(422, problems.join(" "))
}

function requireString(draft, key, problems) {
  if (typeof draft[key] !== "string") problems.push(`${key} is required.`)
}

function validatePostId(postId) {
  if (typeof postId !== "string" || postId.trim() === "") throw new CmsError(400, "postId is required.")
}

function normalizeImageSize(value) {
  const normalized = normalizeName(value ?? "")
  if (normalized === "") return null
  if (normalized === "small") return "SMALL"
  if (normalized === "medium") return "MEDIUM"
  if (normalized === "large") return "LARGE"
  if (normalized === "wide") return "WIDE"
  return value?.toUpperCase() ?? null
}

function emptyToNull(value) {
  return value === "" || value == null ? null : value
}

function isIsoDate(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && new Date(`${value}T00:00:00.000Z`).toISOString().slice(0, 10) === value
}

function isHttpUrl(value) {
  try {
    const url = new URL(value)
    return url.protocol === "http:" || url.protocol === "https:"
  } catch {
    return false
  }
}

function normalizeName(value) {
  return String(value ?? "")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
}

function slugify(value) {
  const slug = normalizeName(value).slice(0, 100)
  return slug || "post"
}
