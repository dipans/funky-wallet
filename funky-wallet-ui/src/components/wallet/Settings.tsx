import { useWalletStore } from '../../store/walletStore'
import { useAccounts } from '../../hooks/useWallet'
import type { Network } from '../../types'
import styles from './Settings.module.css'

const NETWORKS: Network[] = ['ETHEREUM', 'SOLANA', 'BITCOIN']

function Toggle({ checked, onChange, disabled }: { checked: boolean; onChange?: (v: boolean) => void; disabled?: boolean }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      className={`${styles.toggle} ${checked ? styles.toggleOn : ''} ${disabled ? styles.toggleDisabled : ''}`}
      onClick={() => !disabled && onChange?.(!checked)}
      disabled={disabled}
    >
      <span className={styles.toggleThumb} />
    </button>
  )
}

export default function Settings() {
  const { activeAccount, activeNetwork, accounts, setActiveAccount, setActiveNetwork, confirmationSettings, setConfirmationSettings } = useWalletStore()
  useAccounts()

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Settings</h1>

      <section className={`card ${styles.section}`}>
        <h2 className={styles.sectionTitle}>Network</h2>
        <div className={styles.optionGrid}>
          {NETWORKS.map((n) => (
            <button
              key={n}
              className={`${styles.optBtn} ${activeNetwork === n ? styles.optBtnActive : ''}`}
              onClick={() => setActiveNetwork(n)}
            >
              {n}
            </button>
          ))}
        </div>
      </section>

      <section className={`card ${styles.section}`}>
        <h2 className={styles.sectionTitle}>Accounts</h2>
        {accounts.length === 0 && <p className={styles.empty}>No accounts yet.</p>}
        {accounts.map((acc) => (
          <div
            key={acc.id}
            className={`${styles.accountRow} ${activeAccount?.id === acc.id ? styles.accountRowActive : ''}`}
            onClick={() => setActiveAccount(acc)}
          >
            <div>
              <span className={styles.accNetwork}>{acc.network}</span>
              <span className="mono" style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                {acc.address.slice(0, 12)}…{acc.address.slice(-6)}
              </span>
            </div>
            {activeAccount?.id === acc.id && <span className={styles.activePill}>Active</span>}
          </div>
        ))}
      </section>

      <section className={`card ${styles.section}`}>
        <h2 className={styles.sectionTitle}>Transaction Safety</h2>
        <hr className={styles.divider} />
        <div className={styles.toggleRow}>
          <div className={styles.toggleLabel}>
            <span>Require confirmation on mainnet</span>
            <span className={styles.lockIcon}>🔒</span>
          </div>
          <Toggle checked={confirmationSettings.mainnet} disabled />
        </div>
        <div className={styles.toggleRow}>
          <span className={styles.toggleLabel}>Require confirmation on testnet</span>
          <Toggle
            checked={confirmationSettings.testnet}
            onChange={(v) => setConfirmationSettings({ testnet: v })}
          />
        </div>
        <div className={styles.toggleRow}>
          <span className={styles.toggleLabel}>Require confirmation on devnet</span>
          <Toggle
            checked={confirmationSettings.devnet}
            onChange={(v) => setConfirmationSettings({ devnet: v })}
          />
        </div>
        <div className={styles.toggleRow}>
          <span className={styles.toggleLabel}>Require confirmation on local</span>
          <Toggle
            checked={confirmationSettings.local}
            onChange={(v) => setConfirmationSettings({ local: v })}
          />
        </div>
      </section>

      <section className={`card ${styles.section}`}>
        <h2 className={styles.sectionTitle}>About</h2>
        <p className={styles.about}>Funky Wallet v0.1.0 — a non-custodial MPC wallet. Keys never leave the signing layer.</p>
      </section>
    </div>
  )
}
