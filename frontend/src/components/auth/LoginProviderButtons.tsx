import type { ReactElement } from 'react'
import type { OAuthProviderName } from '../../types/auth'
import { lastLoginProviderStorage } from '../../storage/lastLoginProviderStorage'

interface LoginProviderButtonsProps {
  onSelect: (provider: OAuthProviderName) => void
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" className="shrink-0">
      <path
        fill="#4285F4"
        d="M23.52 12.27c0-.85-.08-1.67-.22-2.45H12v4.63h6.47a5.53 5.53 0 0 1-2.4 3.63v3h3.88c2.27-2.09 3.57-5.17 3.57-8.81Z"
      />
      <path
        fill="#34A853"
        d="M12 24c3.24 0 5.96-1.07 7.95-2.92l-3.88-3c-1.08.72-2.46 1.15-4.07 1.15-3.13 0-5.78-2.11-6.73-4.96H1.26v3.11A12 12 0 0 0 12 24Z"
      />
      <path
        fill="#FBBC05"
        d="M5.27 14.27a7.2 7.2 0 0 1 0-4.54v-3.1H1.26a12 12 0 0 0 0 10.75l4.01-3.11Z"
      />
      <path
        fill="#EA4335"
        d="M12 4.77c1.76 0 3.34.6 4.59 1.79l3.44-3.44C17.95 1.19 15.24 0 12 0A12 12 0 0 0 1.26 6.63l4.01 3.1C6.22 6.88 8.87 4.77 12 4.77Z"
      />
    </svg>
  )
}

function KakaoIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" className="shrink-0">
      <path
        fill="#000"
        d="M12 3C6.48 3 2 6.44 2 10.7c0 2.72 1.83 5.11 4.6 6.47-.2.74-.73 2.7-.84 3.12-.13.52.19.51.4.37.17-.11 2.68-1.82 3.77-2.56.66.1 1.35.15 2.07.15 5.52 0 10-3.44 10-7.68C22 6.44 17.52 3 12 3Z"
      />
    </svg>
  )
}

function NaverIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" className="shrink-0">
      <path fill="#fff" d="M14.5 3v9.4L9.5 3H3v18h6V11.6l5 9.4h6.5V3h-6Z" />
    </svg>
  )
}

const PROVIDER_BUTTON_CLASSES: Record<OAuthProviderName, string> = {
  google: 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50',
  kakao: 'bg-[#FEE500] text-black hover:opacity-90',
  naver: 'bg-[#03C75A] text-white hover:opacity-90',
}

const PROVIDER_LABELS: Record<OAuthProviderName, string> = {
  google: 'Google로 로그인',
  kakao: '카카오계정으로 로그인',
  naver: '네이버 아이디로 로그인',
}

const PROVIDER_ICONS: Record<OAuthProviderName, () => ReactElement> = {
  google: GoogleIcon,
  kakao: KakaoIcon,
  naver: NaverIcon,
}

const PROVIDER_ORDER: OAuthProviderName[] = ['kakao', 'naver', 'google']

export function LoginProviderButtons({ onSelect }: LoginProviderButtonsProps) {
  const lastProvider = lastLoginProviderStorage.get()

  return (
    <div className="space-y-3">
      {PROVIDER_ORDER.map((provider) => {
        const Icon = PROVIDER_ICONS[provider]
        return (
          <button
            key={provider}
            type="button"
            onClick={() => onSelect(provider)}
            className={`flex w-full items-center justify-center gap-2.5 rounded-xl py-3 text-sm font-semibold transition ${PROVIDER_BUTTON_CLASSES[provider]}`}
          >
            <Icon />
            {PROVIDER_LABELS[provider]}
            {lastProvider === provider && (
              <span className="ml-1 rounded-full bg-black/10 px-2 py-0.5 text-[10px] font-medium">
                최근 로그인
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}
