import { useEffect } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { setTokenGetter } from '../../api'

export default function ApiTokenProvider({ children }: { children: React.ReactNode }) {
  const { getAccessTokenSilently } = useAuth0()

  useEffect(() => {
    setTokenGetter(() =>
      getAccessTokenSilently({
        authorizationParams: { audience: import.meta.env.VITE_AUTH0_AUDIENCE },
      })
    )
  }, [getAccessTokenSilently])

  return <>{children}</>
}
