import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getLinkedProviders, unlinkSocialAccount } from '../../api/auth'
import type { OAuthProviderName } from '../../types/auth'
import { queryKeys } from '../queryKeys'

export function useLinkedProvidersQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.linkedProviders,
    queryFn: getLinkedProviders,
    enabled,
  })
}

export function useUnlinkSocialAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (provider: OAuthProviderName) => unlinkSocialAccount(provider),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.linkedProviders }),
  })
}
