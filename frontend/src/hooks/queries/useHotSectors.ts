import { useQuery } from '@tanstack/react-query'
import { getHotSectors } from '../../api/market'
import { queryKeys } from '../queryKeys'

// 서버 스냅샷이 장중에만 채워지므로(마감 후엔 빈 배열) 오버레이가 열려 있는 동안만
// 느슨하게 폴링한다 - 검색창은 짧게 머무는 화면이라 30초면 충분하다.
export function useHotSectorsQuery(limit = 5, enabled = true) {
  return useQuery({
    queryKey: queryKeys.hotSectors(limit),
    queryFn: () => getHotSectors(limit),
    staleTime: 30 * 1000,
    enabled,
  })
}
