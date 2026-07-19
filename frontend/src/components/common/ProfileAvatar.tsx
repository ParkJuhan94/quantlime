import { useState } from 'react'

interface ProfileAvatarProps {
  profileImageUrl?: string | null
  nickname?: string
  className?: string
  textSizeClassName?: string
}

// 프로필 사진이 없거나(profileImageUrl null) 깨진 이미지면(onError) 피드
// 게시글 아바타와 같은 패턴으로 닉네임 첫 글자를 원형에 채운다 - 다만
// 피드는 닉네임별로 색이 다른 반면 여긴 무채색(gray) 하나로 고정한다.
export function ProfileAvatar({
  profileImageUrl,
  nickname,
  className = 'h-9 w-9',
  textSizeClassName = 'text-sm',
}: ProfileAvatarProps) {
  const [imageFailed, setImageFailed] = useState(false)

  const showImage = profileImageUrl && !imageFailed

  return (
    <div className={`flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-gray-200 ${className}`}>
      {showImage ? (
        <img
          src={profileImageUrl}
          alt=""
          className="h-full w-full object-cover"
          onError={() => setImageFailed(true)}
        />
      ) : nickname ? (
        <span className={`font-semibold text-gray-500 ${textSizeClassName}`}>{nickname.charAt(0)}</span>
      ) : (
        // 비로그인 등 닉네임조차 없을 땐 기본 사용자 실루엣 아이콘.
        <svg width="60%" height="60%" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="2">
          <circle cx="12" cy="8" r="4" />
          <path d="M4 21c0-4 3.5-7 8-7s8 3 8 7" />
        </svg>
      )}
    </div>
  )
}
