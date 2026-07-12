const SEARCH_HISTORY_KEY = 'ql_recent_searches'
const MAX_ENTRIES = 8

function read(): string[] {
  try {
    const raw = localStorage.getItem(SEARCH_HISTORY_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string') : []
  } catch {
    return []
  }
}

function add(term: string): string[] {
  const trimmed = term.trim()
  if (!trimmed) return read()
  const next = [trimmed, ...read().filter((t) => t !== trimmed)].slice(0, MAX_ENTRIES)
  localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(next))
  return next
}

function remove(term: string): string[] {
  const next = read().filter((t) => t !== term)
  localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(next))
  return next
}

export const searchHistoryStorage = {
  read,
  add,
  remove,
}
