export function EmptyState({ message }: { message: string }) {
  return (
    <p className="rounded-lg border border-dashed border-gray-300 px-4 py-8 text-center text-sm text-gray-500">
      {message}
    </p>
  )
}
