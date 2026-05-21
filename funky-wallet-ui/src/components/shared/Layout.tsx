import { NavLink, Outlet } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import { useQueryClient } from '@tanstack/react-query'
import { useWalletStore } from '../../store/walletStore'
import { useAccounts } from '../../hooks/useWallet'
import ErrorBoundary from './ErrorBoundary'
import { ENV_COLORS } from '../../utils/chain'
import styles from './Layout.module.css'

const nav = [
  { to: '/',         label: 'Dashboard' },
  { to: '/accounts', label: 'Accounts' },
  { to: '/send',     label: 'Send' },
  { to: '/activity', label: 'Activity' },
  { to: '/create',   label: 'New Account' },
  { to: '/settings', label: 'Settings' },
]

export default function Layout() {
  useAccounts()
  const activeAccount = useWalletStore((s) => s.activeAccount)
  const { user, logout } = useAuth0()
  const reset = useWalletStore((s) => s.reset)
  const queryClient = useQueryClient()

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <div className={styles.logo}>
          <span className={styles.logoMark}>FW</span>
          <span className={styles.logoText}>Funky Wallet</span>
        </div>
        <nav className={styles.nav}>
          {nav.map(({ to, label }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `${styles.navItem} ${isActive ? styles.navItemActive : ''}`
              }
            >
              {label}
            </NavLink>
          ))}
        </nav>
        {activeAccount && (
          <div className={styles.accountChip}>
            <div className={styles.chipTop}>
              <span className={styles.chipChainName}>{activeAccount.chainName}</span>
              {activeAccount.environment && (() => {
                const envColor = ENV_COLORS[activeAccount.environment]
                return (
                  <span
                    className={styles.chipEnvBadge}
                    style={{ background: envColor.bg, color: envColor.text }}
                  >
                    {activeAccount.environment}
                  </span>
                )
              })()}
            </div>
            <span className={styles.chipAddress}>
              {activeAccount.address.slice(0, 6)}…{activeAccount.address.slice(-4)}
            </span>
          </div>
        )}
        <div className={styles.userBar}>
          {user?.picture && <img src={user.picture} alt="" className={styles.avatar} />}
          <span className={styles.userEmail}>{user?.email}</span>
          <button
            className={styles.logoutBtn}
            onClick={() => {
            reset()
            queryClient.clear()
            logout({ logoutParams: { returnTo: window.location.origin } })
          }}
          >
            Sign out
          </button>
        </div>
      </aside>
      <main className={styles.main}>
        <ErrorBoundary>
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  )
}
