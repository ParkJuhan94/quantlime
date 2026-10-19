import { NavLink, Outlet } from 'react-router-dom'

// 관리 기능이 늘어나면 여기에 항목을 추가한다(공지 발송 -> 피드 신고 관리 -> 수집 트리거 순으로 이관 예정).
const ADMIN_MENUS = [{ to: '/admin/notifications', label: '공지 발송' }]

export function AdminLayout() {
  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <h1 className="text-lg font-bold text-gray-900">관리자</h1>
        <nav className="flex gap-1.5" aria-label="관리자 메뉴">
          {ADMIN_MENUS.map((menu) => (
            <NavLink
              key={menu.to}
              to={menu.to}
              className={({ isActive }) =>
                `rounded-lg px-3 py-1.5 text-sm font-semibold transition ${
                  isActive ? 'bg-gray-900 text-white' : 'text-gray-600 hover:bg-gray-200'
                }`
              }
            >
              {menu.label}
            </NavLink>
          ))}
        </nav>
      </div>
      <Outlet />
    </div>
  )
}
