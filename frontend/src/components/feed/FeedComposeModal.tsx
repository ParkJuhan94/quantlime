import { useEffect, useRef, useState } from 'react'
import { useMeQuery } from '../../hooks/queries/useMe'
import { useCreateFeedPost, useUpdateFeedPost } from '../../hooks/queries/useFeed'
import { ProfileAvatar } from '../common/ProfileAvatar'
import { FEED_CATEGORIES, type FeedCategory } from '../../mock/feedMock'
import { uploadImage } from '../../api/upload'
import { resolveUploadUrl } from '../../utils/uploadUrl'
import type { FeedPostResponse } from '../../types/feed'

const TITLE_MAX_LENGTH = 200

interface FeedComposeModalProps {
  open: boolean
  onClose: () => void
  // 있으면 수정 모드 - 기존 글 내용으로 폼을 채우고 PATCH로 저장한다
  // (2026-07-17, FeedPostCard의 "수정" 버튼에서 재사용).
  editingPost?: FeedPostResponse | null
}

// 실제 토스증권 피드의 글쓰기 모달(제목 입력 + 주제 선택 + 커뮤니티
// 가이드라인 + 글자수)을 Playwright로 직접 열어보고 구조를 참고해 만들었다.
// "남기기"는 실제로 백엔드에 저장된다(2026-07-16 연동 완료) - 좋아요/댓글은
// 아직 없어 글 작성만 지원한다.
export function FeedComposeModal({ open, onClose, editingPost }: FeedComposeModalProps) {
  const isEditMode = editingPost != null
  const meQuery = useMeQuery(open)
  const createPost = useCreateFeedPost()
  const updatePost = useUpdateFeedPost(editingPost?.id ?? -1)
  const [category, setCategory] = useState<FeedCategory | null>(null)
  const [categoryPickerOpen, setCategoryPickerOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [shakeCategoryPicker, setShakeCategoryPicker] = useState(false)
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imagePreviewUrl, setImagePreviewUrl] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!open) return
    setCategory((editingPost?.category as FeedCategory | undefined) ?? null)
    setCategoryPickerOpen(false)
    setTitle(editingPost?.title ?? '')
    setSubmitted(false)
    setShakeCategoryPicker(false)
    setImageFile(null)
    setImagePreviewUrl(editingPost?.imageUrl ? resolveUploadUrl(editingPost.imageUrl) : null)
    setUploading(false)
    setUploadError(false)
    createPost.reset()
    updatePost.reset()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editingPost])

  // object URL은 브라우저 메모리를 계속 잡고 있으므로 미리보기가 바뀌거나
  // 모달이 닫힐 때 반드시 해제한다. 수정 모드의 기존 이미지는 object URL이
  // 아니라 서버 URL이라 해제 대상이 아니다(blob: 인 경우만 해제).
  useEffect(() => {
    return () => {
      if (imagePreviewUrl?.startsWith('blob:')) URL.revokeObjectURL(imagePreviewUrl)
    }
  }, [imagePreviewUrl])

  function handleImageSelect(file: File | null) {
    if (!file) return
    setImageFile(file)
    setImagePreviewUrl(URL.createObjectURL(file))
    setUploadError(false)
  }

  function handleImageRemove() {
    setImageFile(null)
    setImagePreviewUrl(null)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  useEffect(() => {
    if (!open) return
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open) return null

  const activeMutation = isEditMode ? updatePost : createPost

  // 제목만 실제로 입력을 막는 조건으로 두고, 주제 누락은 버튼을 막는 대신
  // handleSubmit에서 흔들기 애니메이션으로 유도한다(disabled로 막으면
  // 왜 안 눌리는지 알기 어려움).
  const canSubmit = title.trim().length > 0

  async function handleSubmit() {
    if (category === null) {
      setShakeCategoryPicker(true)
      setTimeout(() => setShakeCategoryPicker(false), 400)
      return
    }
    if (!canSubmit) return

    // 이미지를 새로 고르지 않았고 수정 모드라면(기존 이미지 유지 또는
    // 사용자가 × 로 제거) imageFile 자체가 없으므로 업로드를 건너뛴다 -
    // 이 경우 imagePreviewUrl 유무로 유지/제거를 판단한다.
    let imageUrl: string | null = isEditMode && !imageFile ? (imagePreviewUrl ? editingPost.imageUrl : null) : null
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

    await activeMutation.mutateAsync({ category, title: title.trim(), imageUrl })
    setSubmitted(true)
    setTimeout(onClose, 900)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35" onClick={onClose}>
      <div
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl"
      >
        <div className="mb-4 flex items-center justify-between">
          <div className="relative">
            <button
              type="button"
              onClick={() => setCategoryPickerOpen((prev) => !prev)}
              className={`flex items-center gap-1 rounded-lg text-sm font-semibold text-gray-900 ${shakeCategoryPicker ? 'animate-shake text-red-600' : ''}`}
            >
              {category ?? '어떤 주제로 써볼까요?'}
              {/* 주제 선택은 필수인데 예전엔 제출을 시도해야만(shake) 알 수
                  있었다 - 고르기 전부터 "필수"라는 걸 미리 알 수 있도록
                  작은 점을 은은하게 깜빡여둔다(2026-07-17 피드백). */}
              {category === null && (
                <span className="relative flex h-1.5 w-1.5">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-red-400 opacity-75" />
                  <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-red-500" />
                </span>
              )}
              <span className="text-gray-400">›</span>
            </button>
            {categoryPickerOpen && (
              <div className="absolute left-0 top-7 z-10 w-44 rounded-xl border border-gray-100 bg-white p-1.5 shadow-lg">
                {FEED_CATEGORIES.map((option) => (
                  <button
                    key={option}
                    type="button"
                    onClick={() => {
                      setCategory(option)
                      setCategoryPickerOpen(false)
                    }}
                    className="w-full rounded-lg px-2.5 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
                  >
                    {option}
                  </button>
                ))}
              </div>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="flex h-8 w-8 items-center justify-center rounded-full text-gray-400 hover:bg-gray-100"
          >
            ×
          </button>
        </div>

        <div className="mb-4 flex items-start gap-3">
          <ProfileAvatar profileImageUrl={meQuery.data?.profileImageUrl} nickname={meQuery.data?.nickname} />
          {/* 이 입력란이 사실상 게시글 본문이라(별도 본문 필드 없음) "제목을
              입력해주세요"는 어색하다는 피드백으로 문구를 바꿨다. 배경색을
              줘서 여기가 입력 가능한 칸이라는 걸 시각적으로 드러낸다
              (2026-07-16). 이전엔 한 줄 input이라 줄바꿈이 아예 안 먹혔고
              칸도 작았다 - textarea로 바꿔 여러 줄을 자유롭게 쓸 수 있게
              하고 높이도 넉넉하게 키웠다(2026-07-17 피드백). 글자수 제한
              (200자)은 백엔드 컬럼 길이와 맞춰야 해서 그대로 유지.*/}
          <textarea
            value={title}
            onChange={(event) => setTitle(event.target.value.slice(0, TITLE_MAX_LENGTH))}
            placeholder="무슨 이야기를 나눠볼까요?"
            rows={4}
            className="flex-1 resize-none rounded-xl border-none bg-gray-50 px-3 py-2.5 text-base text-gray-900 placeholder-gray-400 outline-none"
          />
        </div>

        {imagePreviewUrl && (
          <div className="relative mb-4 inline-block">
            <img src={imagePreviewUrl} alt="첨부 이미지 미리보기" className="max-h-48 rounded-xl object-cover" />
            <button
              type="button"
              onClick={handleImageRemove}
              aria-label="이미지 제거"
              className="absolute right-1.5 top-1.5 flex h-6 w-6 items-center justify-center rounded-full bg-black/60 text-white hover:bg-black/80"
            >
              ×
            </button>
          </div>
        )}

        <p className="mb-5 rounded-xl bg-gray-50 p-3 text-xs leading-relaxed text-gray-500">
          광고, 비방, 도배성 글을 남기면 활동이 제한될 수 있어요.
          <br />
          건강한 커뮤니티 문화를 함께 만들어가요.
        </p>

        {submitted ? (
          <p className="text-center text-sm font-medium text-gray-900">{isEditMode ? '수정됐어요!' : '게시됐어요!'}</p>
        ) : (
          <>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
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
                  aria-label="이미지 첨부"
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <rect x="3" y="3" width="18" height="18" rx="2" />
                    <circle cx="8.5" cy="8.5" r="1.5" />
                    <path d="m21 15-5-5L5 21" />
                  </svg>
                </button>
                <span className="text-xs text-gray-300">
                  {title.length}/{TITLE_MAX_LENGTH}
                </span>
              </div>
              <button
                type="button"
                disabled={!canSubmit || activeMutation.isPending || uploading}
                onClick={() => void handleSubmit()}
                className="rounded-lg bg-gray-900 px-5 py-2 text-sm font-semibold text-white transition hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-30"
              >
                {uploading
                  ? '이미지 업로드 중...'
                  : activeMutation.isPending
                    ? (isEditMode ? '수정하는 중...' : '남기는 중...')
                    : (isEditMode ? '수정하기' : '남기기')}
              </button>
            </div>
            {uploadError && (
              <p className="mt-2 text-right text-xs text-red-600">이미지 업로드에 실패했어요. 다시 시도해주세요.</p>
            )}
            {activeMutation.isError && (
              <p className="mt-2 text-right text-xs text-red-600">
                {isEditMode ? '수정에 실패했어요. 다시 시도해주세요.' : '글 작성에 실패했어요. 다시 시도해주세요.'}
              </p>
            )}
          </>
        )}
      </div>
    </div>
  )
}
