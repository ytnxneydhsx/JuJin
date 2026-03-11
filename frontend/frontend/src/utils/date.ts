export function formatDateTime(input: string | null | undefined) {
  if (!input) {
    return '-'
  }
  const date = new Date(input)
  if (Number.isNaN(date.getTime())) {
    return input
  }
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
