export function ErrorState({ message }: { message: string }) {
  return <p className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{message}</p>
}
