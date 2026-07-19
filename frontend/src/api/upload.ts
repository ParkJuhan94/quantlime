import { apiClient } from './client'

interface UploadImageResponse {
  imageUrl: string
}

export async function uploadImage(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('image', file)
  const { data } = await apiClient.post<UploadImageResponse>('/api/uploads/images', formData)
  return data.imageUrl
}
