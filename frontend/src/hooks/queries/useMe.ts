import { useQuery } from '@tanstack/react-query'
import { getMe } from '../../api/user'
import { queryKeys } from '../queryKeys'

export function useMeQuery(enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: getMe,
    enabled,
    staleTime: 5 * 60 * 1000,
  })
}
