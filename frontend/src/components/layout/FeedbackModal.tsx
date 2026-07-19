import { useRef, useState } from 'react'
import { sendFeedback, type FeedbackCategory } from '../../api/feedback'
import { uploadImage } from '../../api/upload'

interface FeedbackModalProps {
  open: boolean
  onClose: () => void
}

const CATEGORY_OPTIONS: { value: FeedbackCategory; label: string }[] = [
  { value: 'BUG', label: '버그 제보' },
  { value: 'FEATURE', label: '기능 개선' },
  { value: 'OTHER', label: '기타' },
]

// 사이드패널의 "의견 보내기" - 백엔드가 Slack Incoming Webhook으로 곧장
// 전달한다. Incoming Webhook 자체는 파일 업로드를 지원하지 않지만
// (텍스트/블록 메시지만 가능), 이미지를 먼저 /api/uploads/images로 올려
// 절대 URL 링크로 메시지에 넣으면 Slack이 자동으로 미리보기를
// 언퍼널링해준다(2026-07-17, 파일 업로드 인프라 도입으로 가능해짐).
export function FeedbackModal({ open, onClose }: FeedbackModalProps) {
  const [category, setCategory] = useState<FeedbackCategory>('BUG')
  const [message, setMessage] = useState('')
  const [status, setStatus] = useState<'idle' | 'sending' | 'sent' | 'error'>('idle')
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imagePreviewUrl, setImagePreviewUrl] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  if (!open) return null

  function handleClose() {
    setMessage('')
    setCategory('BUG')
    setStatus('idle')
    if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl)
    setImageFile(null)
    setImagePreviewUrl(null)
    setUploadError(false)
    onClose()
  }

  function handleImageSelect(file: File | null) {
    if (!file) return
    setImageFile(file)
    setImagePreviewUrl(URL.createObjectURL(file))
    setUploadError(false)
  }

  function handleImageRemove() {
    if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl)
    setImageFile(null)
    setImagePreviewUrl(null)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  async function handleSubmit() {
    if (!message.trim() || status === 'sending') return

    let imageUrl: string | null = null
    if (imageFile) {
      setUploading(true)
      try {
        imageUrl = await uploadImage(imageFile)
      } catch {
        setUploadError(true)
        setUploading(false)
        return
      }
      setUploading(false)
    }

    setStatus('sending')
    try {
      await sendFeedback(category, message.trim(), window.location.pathname, imageUrl)
      setStatus('sent')
    } catch {
      setStatus('error')
    }
  }

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/35" onClick={handleClose}>
      <div
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-sm rounded-2xl bg-white p-5 shadow-2xl"
      >
        {status === 'sent' ? (
          <div className="py-4 text-center">
            <p className="mb-4 text-sm font-medium text-gray-900">의견이 전달됐어요. 감사합니다!</p>
            <button
              type="button"
              onClick={handleClose}
              className="w-full rounded-lg bg-gray-900 py-2.5 text-sm font-semibold text-white hover:bg-gray-800"
            >
              닫기
            </button>
          </div>
        ) : (
          <>
            <p className="mb-3 text-sm font-semibold text-gray-900">의견 보내기</p>
            <div className="mb-3 flex gap-1.5">
              {CATEGORY_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => setCategory(option.value)}
                  className={`flex-1 rounded-lg py-1.5 text-xs font-semibold transition ${
                    category === option.value
                      ? 'bg-gray-900 text-white'
                      : 'border border-gray-200 text-gray-500 hover:bg-gray-50'
                  }`}
                >
                  {option.label}
                </button>
              ))}
            </div>
            <textarea
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              placeholder="버그, 불편한 점, 있으면 좋겠는 기능을 자유롭게 적어주세요"
              rows={5}
              maxLength={2000}
              autoFocus
              className="mb-1 w-full resize-none rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none focus:border-gray-400"
            />
            {imagePreviewUrl && (
              <div className="relative mb-3 inline-block">
                <img src={imagePreviewUrl} alt="첨부 이미지 미리보기" className="max-h-32 rounded-lg object-cover" />
                <button
                  type="button"
                  onClick={handleImageRemove}
                  aria-label="이미지 제거"
                  className="absolute right-1 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-black/60 text-xs text-white hover:bg-black/80"
                >
                  ×
                </button>
              </div>
            )}
            <div className="mb-3 flex items-center gap-2">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png,image/gif,image/webp"
                className="hidden"
                onChange={(event) => handleImageSelect(event.target.files?.[0] ?? null)}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="flex items-center gap-1 rounded-lg px-1.5 py-1 text-[11px] text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="3" width="18" height="18" rx="2" />
                  <circle cx="8.5" cy="8.5" r="1.5" />
                  <path d="m21 15-5-5L5 21" />
                </svg>
                이미지 첨부
              </button>
            </div>
            {uploadError && (
              <p className="mb-3 text-xs text-red-600">이미지 업로드에 실패했어요. 다시 시도해주세요.</p>
            )}
            {status === 'error' && (
              <p className="mb-3 text-xs text-red-600">전송에 실패했어요. 잠시 후 다시 시도해주세요.</p>
            )}
            <div className="flex gap-2">
              <button
                type="button"
                onClick={handleClose}
                className="flex-1 rounded-lg border border-gray-200 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-50"
              >
                닫기
              </button>
              <button
                type="button"
                disabled={!message.trim() || status === 'sending' || uploading}
                onClick={() => void handleSubmit()}
                className="flex-1 rounded-lg bg-gray-900 py-2 text-sm font-semibold text-white transition disabled:cursor-not-allowed disabled:opacity-30"
              >
                {uploading ? '이미지 업로드 중...' : status === 'sending' ? '보내는 중...' : '보내기'}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
