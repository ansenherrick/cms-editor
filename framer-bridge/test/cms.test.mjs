import assert from "node:assert/strict"
import test from "node:test"
import { executeCms, CmsError } from "../src/cms.mjs"

const config = { websiteId: "personal-site", collection: "blog-posts" }

function fixture({ fields = defaultFields(), collections } = {}) {
  const calls = []
  const items = [
    makeItem({
      id: "item-1",
      slug: "hello-world",
      draft: false,
      fieldData: {
        title: { type: "string", value: "Hello World" },
        date: { type: "date", value: "2026-09-06T00:00:00.000Z" },
        body: { type: "formattedText", value: "<p>Hello.</p>" },
        extra: { type: "string", value: "keep me" },
      },
      calls,
    }),
  ]

  const collection = {
    id: "collection-1",
    name: "Blog Posts",
    managedBy: "user",
    readonly: false,
    async getFields() {
      calls.push(["getFields"])
      return fields
    },
    async getItems() {
      calls.push(["getItems"])
      return items
    },
    async addItems(newItems) {
      calls.push(["addItems", newItems])
      for (const newItem of newItems) {
        items.push(makeItem({
          id: `item-${items.length + 1}`,
          slug: newItem.slug,
          draft: newItem.draft,
          fieldData: newItem.fieldData,
          calls,
        }))
      }
    },
  }

  return {
    calls,
    items,
    framer: {
      async getCollections() {
        calls.push(["getCollections"])
        return collections ?? [collection]
      },
    },
    collection,
  }
}

function defaultFields() {
  return [
    { id: "title", name: "Title", type: "string", required: true },
    { id: "date", name: "Date", type: "date", required: true },
    { id: "body", name: "Body Text", type: "formattedText", required: true },
    { id: "sub", name: "Sub Text", type: "formattedText" },
    { id: "image", name: "Image Link", type: "link" },
    { id: "alt", name: "Image Alt Text", type: "string" },
    {
      id: "size",
      name: "Image Size",
      type: "enum",
      cases: [
        { id: "case-small", name: "Small" },
        { id: "case-medium", name: "Medium" },
        { id: "case-large", name: "Large" },
        { id: "case-wide", name: "Wide" },
      ],
    },
    { id: "link", name: "Optional link", type: "link" },
    { id: "linkText", name: "Link text", type: "string" },
    { id: "extra", name: "Internal Notes", type: "string" },
  ]
}

function makeItem({ id, slug, draft, fieldData, calls }) {
  return {
    id,
    slug,
    draft,
    fieldData: { ...fieldData },
    async setAttributes(attributes) {
      calls.push(["setAttributes", id, attributes])
      if (attributes.fieldData) this.fieldData = { ...this.fieldData, ...attributes.fieldData }
      if (typeof attributes.draft === "boolean") this.draft = attributes.draft
      return this
    },
    async remove() {
      calls.push(["remove", id])
    },
  }
}

const validDraft = {
  date: "2026-09-07",
  title: "Updated Title",
  imageUrl: "https://assets.ansenherrick.com/photo.jpg",
  imageAltText: "A photo",
  imageSize: "WIDE",
  subheading: "<p>Sub.</p>",
  link: "https://ansenherrick.com",
  linkText: "Read more",
  bodyText: "<p>Updated.</p>",
}

test("lists Framer items as app posts", async () => {
  const ctx = fixture()
  const posts = await executeCms(ctx.framer, config, { operation: "list", websiteId: "personal-site" })

  assert.equal(posts.length, 1)
  assert.equal(posts[0].id, "item-1")
  assert.equal(posts[0].title, "Hello World")
  assert.equal(posts[0].date, "2026-09-06")
  assert.equal(posts[0].bodyText, "<p>Hello.</p>")
  assert.equal(posts[0].isPublished, true)
})

test("inspect reads collection schema without reading items", async () => {
  const ctx = fixture()
  const result = await executeCms(ctx.framer, config, { operation: "inspect", websiteId: "personal-site" })

  assert.equal(result.collection.name, "Blog Posts")
  assert.equal(result.fields.bodyText.type, "formattedText")
  assert.equal(ctx.calls.some((call) => call[0] === "getItems"), false)
})

test("create writes a draft Framer item and returns it", async () => {
  const ctx = fixture()
  const post = await executeCms(ctx.framer, config, { operation: "create", websiteId: "personal-site", draft: validDraft })

  const add = ctx.calls.find((call) => call[0] === "addItems")
  assert.equal(add[1][0].draft, true)
  assert.match(add[1][0].slug, /^updated-title-[a-f0-9-]{8}$/)
  assert.equal(add[1][0].fieldData.body.contentType, "auto")
  assert.equal(add[1][0].fieldData.size.value, "case-wide")
  assert.equal(post.title, "Updated Title")
})

test("update preserves slug, draft state, and unknown Framer fields", async () => {
  const ctx = fixture()
  const post = await executeCms(ctx.framer, config, {
    operation: "update",
    websiteId: "personal-site",
    postId: "item-1",
    draft: validDraft,
  })

  const update = ctx.calls.find((call) => call[0] === "setAttributes")
  assert.deepEqual(Object.keys(update[2]).sort(), ["fieldData"])
  assert.equal(ctx.items[0].slug, "hello-world")
  assert.equal(ctx.items[0].draft, false)
  assert.equal(ctx.items[0].fieldData.extra.value, "keep me")
  assert.equal(post.imageSize, "WIDE")
})

test("setPublished only toggles Framer draft status", async () => {
  const ctx = fixture()
  const post = await executeCms(ctx.framer, config, {
    operation: "setPublished",
    websiteId: "personal-site",
    postId: "item-1",
    isPublished: false,
  })

  const update = ctx.calls.find((call) => call[0] === "setAttributes")
  assert.deepEqual(update[2], { draft: true })
  assert.equal(post.isPublished, false)
})

test("rejects wrong website before reading Framer", async () => {
  const ctx = fixture()
  await assert.rejects(
    executeCms(ctx.framer, config, { operation: "list", websiteId: "other-site" }),
    (error) => error instanceof CmsError && error.status === 403,
  )
  assert.deepEqual(ctx.calls, [])
})

test("missing item is not upserted", async () => {
  const ctx = fixture()
  await assert.rejects(
    executeCms(ctx.framer, config, { operation: "update", websiteId: "personal-site", postId: "missing", draft: validDraft }),
    (error) => error instanceof CmsError && error.status === 404,
  )
  assert.equal(ctx.calls.some((call) => call[0] === "addItems"), false)
})

test("clears optional fields with Framer-compatible empty values", async () => {
  const ctx = fixture()
  await executeCms(ctx.framer, config, {
    operation: "update",
    websiteId: "personal-site",
    postId: "item-1",
    draft: { date: "2026-09-07", title: "Title", bodyText: "Body" },
  })

  const update = ctx.calls.find((call) => call[0] === "setAttributes")
  assert.equal(update[2].fieldData.sub.value, "")
  assert.equal(update[2].fieldData.image.value, null)
  assert.equal(update[2].fieldData.size.value, "")
})

test("rejects missing optional mapped field when a draft needs it", async () => {
  const fields = defaultFields().filter((field) => field.id !== "link")
  const ctx = fixture({ fields })
  await assert.rejects(
    executeCms(ctx.framer, config, { operation: "update", websiteId: "personal-site", postId: "item-1", draft: validDraft }),
    (error) => error instanceof CmsError && error.status === 409,
  )
})

test("rejects extra required Framer fields on create", async () => {
  const fields = [...defaultFields(), { id: "requiredExtra", name: "Required Extra", type: "string", required: true }]
  const ctx = fixture({ fields })
  await assert.rejects(
    executeCms(ctx.framer, config, { operation: "create", websiteId: "personal-site", draft: validDraft }),
    (error) => error instanceof CmsError && error.status === 409,
  )
})

test("rejects ambiguous collections and wrong field types", async () => {
  const first = fixture().collection
  const second = { ...fixture().collection, id: "collection-2" }
  const ambiguous = fixture({ collections: [first, second] })
  await assert.rejects(
    executeCms(ambiguous.framer, config, { operation: "list", websiteId: "personal-site" }),
    (error) => error instanceof CmsError && error.status === 409,
  )

  const fields = defaultFields().map((field) => field.id === "body" ? { ...field, type: "string" } : field)
  const wrongType = fixture({ fields })
  await assert.rejects(
    executeCms(wrongType.framer, config, { operation: "list", websiteId: "personal-site" }),
    (error) => error instanceof CmsError && error.status === 409,
  )
})
