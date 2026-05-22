import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useWalletStore } from '../../store/walletStore'
import { useAccounts } from '../../hooks/useWallet'
import { settingsApi } from '../../api'
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

  const qc = useQueryClient()
  const { data: appSettings } = useQuery({
    queryKey: ['settings'],
    queryFn: settingsApi.get,
  })

  const solanaAccounts = accounts.filter((a) => a.networkType === 'SOLANA')
  const [customFunderAddress, setCustomFunderAddress] = useState('')

  const saveFunder = useMutation({
    mutationFn: (address: string) => settingsApi.setSolanaNonceFunder(address),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['settings'] }),
  })

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
        <h2 className={styles.sectionTitle}>Solana — Nonce Funder</h2>
        <p className={styles.settingDesc}>
          This account pays for creating durable nonce accounts (~0.00145 SOL each) used in
          Solana MPC transactions. The private key must be configured via{' '}
          <code>SOLANA_NONCE_FUNDER_KEYPAIR</code> in the chain adapter.
        </p>

        {appSettings?.solanaNonceFunderConfigured ? (
          <div className={styles.funderStatus}>
            <span className={styles.funderBadge}>✓ Keypair configured</span>
            <span className="mono" style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
              {appSettings.solanaNonceFunderKeypairAddress}
            </span>
          </div>
        ) : (
          <p className={styles.funderWarning}>
            ⚠ No keypair configured — nonce accounts will use a placeholder address.
            Set <code>SOLANA_NONCE_FUNDER_KEYPAIR</code> in the Solana chain adapter.
          </p>
        )}

        <hr className={styles.divider} />
        <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 8 }}>
          Select from your Solana accounts:
        </p>
        <div className={styles.optionGrid}>
          {solanaAccounts.length === 0 && (
            <p className={styles.empty}>No Solana accounts yet.</p>
          )}
          {solanaAccounts.map((acc) => (
            <button
              key={acc.address}
              className={`${styles.optBtn} ${
                appSettings?.solanaNonceFunderAddress === acc.address ? styles.optBtnActive : ''
              }`}
              onClick={() => saveFunder.mutate(acc.address)}
              title={acc.address}
            >
              {acc.address.slice(0, 8)}…{acc.address.slice(-6)}
            </button>
          ))}
        </div>

        <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: '12px 0 6px' }}>
          Or enter an address manually:
        </p>
        <div style={{ display: 'flex', gap: 8 }}>
          <input
            className={styles.addressInput}
            placeholder="Solana address (base58)"
            value={customFunderAddress}
            onChange={(e) => setCustomFunderAddress(e.target.value)}
          />
          <button
            className="btn btn-primary btn-sm"
            disabled={!customFunderAddress.trim() || saveFunder.isPending}
            onClick={() => { saveFunder.mutate(customFunderAddress.trim()); setCustomFunderAddress('') }}
          >
            {saveFunder.isPending ? 'Saving…' : 'Save'}
          </button>
        </div>

        {appSettings?.solanaNonceFunderAddress && (
          <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 8 }}>
            Saved: <span className="mono">{appSettings.solanaNonceFunderAddress}</span>
          </p>
        )}
      </section>

      <section className={`card ${styles.section}`}>
        <h2 className={styles.sectionTitle}>About</h2>
        <p className={styles.about}>Funky Wallet v0.1.0 — a non-custodial MPC wallet. Keys never leave the signing layer.</p>
      </section>
    </div>
  )
}
