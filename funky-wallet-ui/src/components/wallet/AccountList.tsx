import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useWalletStore } from '../../store/walletStore'
import { useBalance } from '../../hooks/useWallet'
import ReceiveModal from './ReceiveModal'
import type { Account, NetworkType } from '../../types'
import styles from './AccountList.module.css'

const NETWORK_TYPE_LABELS: NetworkType[] = ['EVM', 'SOLANA', 'BITCOIN']

function AccountCard({ account, isActive, onClick }: { account: Account; isActive: boolean; onClick: () => void }) {
  const navigate = useNavigate()
  const { data: balance } = useBalance(account.address)
  const isMainnet = account.environment === 'MAINNET'
  const [showReceive, setShowReceive] = useState(false)

  return (
    <>
      <div
        className={`${styles.card} ${isActive ? styles.cardActive : ''}`}
        onClick={onClick}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => e.key === 'Enter' && onClick()}
      >
        <div className={styles.cardTop}>
          <span className={styles.dot} style={{ color: isActive ? 'var(--accent)' : 'var(--text-muted)' }}>
            {isActive ? '●' : '○'}
          </span>
          <span
            className={styles.chainName}
            style={isMainnet ? { color: 'var(--error)' } : undefined}
          >
            {account.chainName}
          </span>
          <span className={styles.balance}>
            {balance ? `${balance.amount} ${balance.symbol}` : '—'}
          </span>
        </div>
        <span className={styles.address}>{account.address}</span>
        <div className={styles.actions} onClick={(e) => e.stopPropagation()}>
          <button
            className={`btn btn-sm btn-ghost ${styles.actionBtn}`}
            onClick={() => navigate('/send', { state: { fromAddress: account.address } })}
          >
            Send
          </button>
          <button
            className={`btn btn-sm btn-ghost ${styles.actionBtn}`}
            onClick={() => setShowReceive(true)}
          >
            Receive
          </button>
        </div>
      </div>
      {showReceive && (
        <ReceiveModal account={account} onClose={() => setShowReceive(false)} />
      )}
    </>
  )
}

export default function AccountList() {
  const navigate = useNavigate()
  const { accounts, activeAccount, setActiveAccount } = useWalletStore()

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>My Accounts</h1>
        <button className="btn btn-primary btn-sm" onClick={() => navigate('/create')}>
          + New Account
        </button>
      </div>
      <hr className={styles.divider} />

      {NETWORK_TYPE_LABELS.map((type) => {
        const group = accounts.filter((a) => a.networkType === type)
        return (
          <section key={type} className={styles.section}>
            <h2 className={styles.sectionTitle}>{type}</h2>
            {group.length === 0 ? (
              <p className={styles.empty}>
                No accounts — <button className={styles.addLink} onClick={() => navigate('/create')}>Add one</button>
              </p>
            ) : (
              <div className={styles.cardList}>
                {group.map((acc) => (
                  <AccountCard
                    key={acc.id}
                    account={acc}
                    isActive={activeAccount?.id === acc.id}
                    onClick={() => setActiveAccount(acc)}
                  />
                ))}
              </div>
            )}
          </section>
        )
      })}
    </div>
  )
}
