import { Routes, Route, Navigate } from 'react-router-dom'
import AuthGuard from './components/auth/AuthGuard'
import Layout from './components/shared/Layout'
import Dashboard from './components/wallet/Dashboard'
import SendTransaction from './components/wallet/SendTransaction'
import Receive from './components/wallet/Receive'
import CreateAccount from './components/auth/CreateAccount'
import Activity from './components/wallet/Activity'
import Settings from './components/wallet/Settings'
import AccountList from './components/wallet/AccountList'

export default function App() {
  return (
    <AuthGuard>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Dashboard />} />
          <Route path="accounts" element={<AccountList />} />
          <Route path="send" element={<SendTransaction />} />
          <Route path="receive" element={<Receive />} />
          <Route path="create" element={<CreateAccount />} />
          <Route path="activity" element={<Activity />} />
          <Route path="settings" element={<Settings />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </AuthGuard>
  )
}
