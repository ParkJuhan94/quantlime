import { useQuery } from '@tanstack/react-query'
import { getTelegramChannels, getVideoChannels } from '../../api/admin'
import { queryKeys } from '../queryKeys'

export function useAdminVideoChannelsQuery() {
  return useQuery({ queryKey: queryKeys.adminVideoChannels, queryFn: getVideoChannels })
}

export function useAdminTelegramChannelsQuery() {
  return useQuery({ queryKey: queryKeys.adminTelegramChannels, queryFn: getTelegramChannels })
}
