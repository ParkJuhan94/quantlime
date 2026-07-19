import { apiClient } from './client'

export type FeedbackCategory = 'BUG' | 'FEATURE' | 'OTHER'

export async function sendFeedback(
  category: FeedbackCategory,
  message: string,
  pageUrl: string,
  imageUrl?: string | null,
): Promise<void> {
  await apiClient.post('/api/feedback', { category, message, pageUrl, imageUrl })
}
