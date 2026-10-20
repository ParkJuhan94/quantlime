import { NavLink, Outlet } from 'react-router-dom'

// 관리 기능이 늘어나면 그룹과 항목만 추가한다.
const ADMIN_MENU_GROUPS = [
  {
    title: '커뮤니티',
    menus: [
      { to: '/admin/notifications', label: '공지 발송' },
      { to: '/admin/moderation', label: '신고 관리' },
    ],
  },
  {
    title: '데이터',
    menus: [
      { to: '/admin/video-feed', label: '영상 수집' },
      { to: '/admin/telegram-feed', label: '텔레그램 수집' },
      { to: '/admin/backtest', label: '백테스트' },
    ],
  },
]

// 선택된 메뉴는 글자색이 아니라 배경색으로 표시한다(frontend/CLAUDE.md 디자인 규칙).
function menuClass(isActive: boolean) {
  return `rounded-lg px-3 py-2 text-sm font-semibold transition ${
    isActive ? 'bg-gray-100 text-gray-900' : 'text-gray-500 hover:bg-gray-50 hover:text-gray-700'
  }`
}

export function AdminLayout() {
  return (
    <div className="mx-auto max-w-5xl">
      <h1 className="mb-4 text-lg font-bold text-gray-900">관리자</h1>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
        {/* 넓은 화면은 왼쪽 고정 메뉴, 좁은 화면은 가로로 스크롤되는 한 줄 메뉴 */}
        <nav
          aria-label="관리자 메뉴"
          className="flex gap-4 overflow-x-auto rounded-2xl border border-gray-100 bg-white p-2 lg:sticky lg:top-6 lg:w-52 lg:shrink-0 lg:flex-col lg:gap-3 lg:overflow-visible lg:p-3"
        >
          {ADMIN_MENU_GROUPS.map((group) => (
            <div key={group.title} className="flex shrink-0 items-center gap-1 lg:flex-col lg:items-stretch">
              <span className="px-3 text-xs font-semibold text-gray-400 lg:pb-1">{group.title}</span>
              {group.menus.map((menu) => (
                <NavLink key={menu.to} to={menu.to} className={({ isActive }) => menuClass(isActive)}>
                  {menu.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="min-w-0 flex-1">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
