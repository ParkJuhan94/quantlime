// 백엔드 FeedCategory enum의 한글 라벨과 정확히 일치해야 한다
// (backend com.quantlime.feed.domain.FeedCategory 참고) - 글쓰기 시 이
// 라벨 그대로 전송한다.
export type FeedCategory = '국내주식토론' | '미국주식이야기' | '아무말대잔치' | '수익인증'
export type FeedFilter = FeedCategory | '전체'

export const FEED_CATEGORIES: FeedCategory[] = ['국내주식토론', '미국주식이야기', '아무말대잔치', '수익인증']

// 증빙 이미지(체결내역 캡처 등) 첨부가 필수인 주제 - 백엔드
// FeedCategory.imageRequired와 일치해야 한다.
export const IMAGE_REQUIRED_CATEGORIES: FeedCategory[] = ['수익인증']
