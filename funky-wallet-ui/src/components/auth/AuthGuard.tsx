import { useAuth0 } from '@auth0/auth0-react'
import ApiTokenProvider from './ApiTokenProvider'

const AUTH_DISABLED = import.meta.env.VITE_AUTH_DISABLED === 'true'

export default function AuthGuard({ children }: { children: React.ReactNode }) {
  // e2e / CI: skip Auth0 entirely — backend has no JWT validation (Istio handles prod)
  if (AUTH_DISABLED) return <>{children}</>

  return <AuthGuardInner>{children}</AuthGuardInner>
}

function AuthGuardInner({ children }: { children: React.ReactNode }) {
  const { isLoading, isAuthenticated, loginWithRedirect } = useAuth0()

  if (isLoading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', background: 'var(--bg)' }}>
        <span className="spinner" style={{ width: 32, height: 32 }} />
      </div>
    )
  }

  if (!isAuthenticated) {
    loginWithRedirect()
    return null
  }

  return <ApiTokenProvider>{children}</ApiTokenProvider>
}
