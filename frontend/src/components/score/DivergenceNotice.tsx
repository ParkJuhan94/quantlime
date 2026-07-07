export function DivergenceNotice({ message }: { message: string | null }) {
  if (!message) {
    return null
  }
  return <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">⚠ {message}</p>
}
