import { useState } from 'react'

// FeedPostCard(커뮤니티 피드)의 아바타 폴백 색상 규칙과 동일하게 맞춘다 -
// 프로필 사진이 없거나 깨졌을 때(onError) 채널명 이니셜 원으로 대체.
const AVATAR_COLORS = [
  'bg-blue-100 text-blue-700',
  'bg-pink-100 text-pink-700',
  'bg-gray-200 text-gray-600',
  'bg-purple-100 text-purple-700',
  'bg-amber-100 text-amber-700',
  'bg-emerald-100 text-emerald-700',
]

function avatarColorClass(name: string): string {
  const hash = name.split('').reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}

interface VideoFeedChannelAvatarProps {
  name: string
  profileImageUrl: string | null
  channelUrl: string | null
}

export function VideoFeedChannelAvatar({ name, profileImageUrl, channelUrl }: VideoFeedChannelAvatarProps) {
  const [imageFailed, setImageFailed] = useState(false)
  const showImage = Boolean(profileImageUrl) && !imageFailed

  const content = (
    <span className="flex items-center gap-1.5">
      <span
        className={`flex h-6 w-6 shrink-0 items-center justify-center overflow-hidden rounded-full text-[11px] font-semibold ${showImage ? '' : avatarColorClass(name)}`}
      >
        {showImage ? (
          <img
            src={profileImageUrl ?? undefined}
            alt=""
            className="h-full w-full object-cover"
            onError={() => setImageFailed(true)}
          />
        ) : (
          name.charAt(0)
        )}
      </span>
      <span className="font-medium text-gray-700">{name}</span>
    </span>
  )

  if (!channelUrl) {
    return content
  }

  return (
    <a href={channelUrl} target="_blank" rel="noreferrer" className="hover:underline">
      {content}
    </a>
  )
}
