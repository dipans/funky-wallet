import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueries } from '@tanstack/react-query'
import { useWalletStore } from '../../store/walletStore'
import { useTransactions } from '../../hooks/useWallet'
import { accountApi } from '../../api'
import type { Account, Network, NetworkType, Transaction } from '../../types'
import { explorerTxUrl } from '../../utils/explorer'
import styles from './Dashboard.module.css'

// Single source of truth for all network metadata
const NETWORKS: {
  networkValue: Network
  networkType: NetworkType
  label: string
  symbol: string
}[] = [
  { networkValue: 'ETHEREUM', networkType: 'EVM',     label: 'Ethereum / EVM', symbol: 'ETH' },
  { networkValue: 'SOLANA',   networkType: 'SOLANA',  label: 'Solana',          symbol: 'SOL' },
  { networkValue: 'BITCOIN',  networkType: 'BITCOIN', label: 'Bitcoin',         symbol: 'BTC' },
]

type SortOrder = 'newest' | 'oldest' | 'amount-desc' | 'amount-asc'

function sortTxs(txs: Transaction[], order: SortOrder): Transaction[] {
  return [...txs].sort((a, b) => {
    switch (order) {
      case 'oldest':      return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
      case 'amount-desc': return parseFloat(b.amount) - parseFloat(a.amount)
      case 'amount-asc':  return parseFloat(a.amount) - parseFloat(b.amount)
      default:            return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    }
  })
}

// ── NetworkCard ───────────────────────────────────────────────────────────────

function NetworkCard({
  label, symbol, accounts, selected, dimmed, onToggle,
}: {
  networkType: NetworkType
  label: string
  symbol: string
  accounts: Account[]
  selected: boolean
  dimmed: boolean
  onToggle: () => void
}) {
  const navigate = useNavigate()
  const balanceResults = useQueries({
    queries: accounts.map((acc) => ({
      queryKey: ['balance', acc.address],
      queryFn:  () => accountApi.getBalance(acc.address),
      staleTime: 30_000,
      refetchInterval: 30_000,
    })),
  })

  const isLoading = balanceResults.some((r) => r.isLoading)
  const total = balanceResults.reduce((sum, r) => sum + parseFloat(r.data?.amount ?? '0'), 0)

  return (
    <div
      role="button"
      tabIndex={0}
      aria-pressed={selected}
      className={`card ${styles.networkCard} ${selected ? styles.networkCardSelected : ''} ${dimmed ? styles.networkCardDimmed : ''}`}
      onClick={onToggle}
      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && onToggle()}
    >
      <div className={styles.networkCardTop}>
        <span className={styles.networkLabel}>{label}</span>
        <div className={styles.networkCardTopRight}>
          {selected && <span className={styles.selectedBadge}>✓</span>}
          <span className={styles.accountCount}>
            {accounts.length} {accounts.length === 1 ? 'account' : 'accounts'}
          </span>
        </div>
      </div>

      <div className={styles.networkBalance}>
        {isLoading && accounts.length > 0 ? (
          <div className={styles.balanceSkeleton} />
        ) : (
          <>
            <span className={styles.balanceAmount}>
              {total.toFixed(accounts.length > 0 ? 6 : 0)}
            </span>
            <span className={styles.symbol}>{symbol}</span>
          </>
        )}
      </div>

      {accounts.length > 0 && (
        <button
          className={`btn btn-sm btn-ghost ${styles.sendBtn}`}
          onClick={(e) => { e.stopPropagation(); navigate('/accounts') }}
        >
          View accounts →
        </button>
      )}
    </div>
  )
}

// ── TxRow ─────────────────────────────────────────────────────────────────────

function TxRow({ tx }: { tx: Transaction }) {
  const isOut = tx.status !== 'RECEIVED'
  return (
    <div className={styles.txRow}>
      <div className={`${styles.txDir} ${isOut ? styles.txOut : styles.txIn}`}>
        {isOut ? '↑ Sent' : '↓ Received'}
      </div>
      <div className={styles.txAddr}>
        {isOut
          ? `To: ${tx.toAddress.slice(0, 8)}…${tx.toAddress.slice(-4)}`
          : `From: ${tx.fromAddress.slice(0, 8)}…${tx.fromAddress.slice(-4)}`}
      </div>
      <div className={styles.txAmount}>
        {isOut ? '-' : '+'}{tx.amount} {tx.symbol}
      </div>
      <span className={`badge badge-${tx.status.toLowerCase()}`}>{tx.status}</span>
      {tx.hash && (
        <a
          href={explorerTxUrl(tx.hash, tx.network)}
          target="_blank"
          rel="noopener noreferrer"
          className={styles.txHashLink}
          title={tx.hash}
          onClick={(e) => e.stopPropagation()}
        >
          ↗
        </a>
      )}
    </div>
  )
}

// ── Dashboard ─────────────────────────────────────────────────────────────────

export default function Dashboard() {
  const navigate = useNavigate()
  const accounts = useWalletStore((s) => s.accounts)

  const [filterNetwork, setFilterNetwork] = useState<Network | undefined>(undefined)
  const [filterAddress, setFilterAddress] = useState<string | undefined>(undefined)
  const [sortOrder, setSortOrder]         = useState<SortOrder>('newest')

  const { data: allTxs } = useTransactions(filterAddress)

  const totalAccounts = accounts.length

  // Cards are dimmed when a different card is selected
  const anySelected = filterNetwork !== undefined

  // Account dropdown scoped to selected network
  const selectedNetworkType = filterNetwork
    ? NETWORKS.find((n) => n.networkValue === filterNetwork)?.networkType
    : undefined
  const filteredAccounts = selectedNetworkType
    ? accounts.filter((a) => a.networkType === selectedNetworkType)
    : accounts

  // Client-side: filter by network, then sort
  const displayTxs = sortTxs(
    filterNetwork ? (allTxs?.filter((tx) => tx.network === filterNetwork) ?? []) : (allTxs ?? []),
    sortOrder,
  )

  function handleNetworkToggle(network: Network) {
    const next = filterNetwork === network ? undefined : network
    setFilterNetwork(next)
    // Reset account filter if it doesn't belong to the new network
    if (next && filterAddress) {
      const nt = NETWORKS.find((n) => n.networkValue === next)?.networkType
      if (!accounts.find((a) => a.address === filterAddress && a.networkType === nt)) {
        setFilterAddress(undefined)
      }
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <h1 className={styles.pageTitle}>Portfolio</h1>
        <span className={styles.accountTotal}>
          {totalAccounts} {totalAccounts === 1 ? 'account' : 'accounts'}
        </span>
      </div>

      {/* Selectable network cards — click to filter Recent, click again to clear */}
      <div className={styles.networkGrid}>
        {NETWORKS.map(({ networkValue, networkType, label, symbol }) => (
          <NetworkCard
            key={networkType}
            networkType={networkType}
            label={label}
            symbol={symbol}
            accounts={accounts.filter((a) => a.networkType === networkType)}
            selected={filterNetwork === networkValue}
            dimmed={anySelected && filterNetwork !== networkValue}
            onToggle={() => handleNetworkToggle(networkValue)}
          />
        ))}
      </div>

      {/* Actions */}
      <div className={styles.actions}>
        <button className="btn btn-primary" onClick={() => navigate('/send')}>Send</button>
        <button className="btn btn-ghost" onClick={() => navigate('/create')}>+ New Account</button>
      </div>

      {/* Recent transactions */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <div className={styles.sectionTitleGroup}>
            <h2 className={styles.sectionTitle}>Recent</h2>
            {filterNetwork && (
              <span className={styles.networkPill}>
                {NETWORKS.find((n) => n.networkValue === filterNetwork)?.label}
                <button
                  className={styles.pillClear}
                  onClick={() => { setFilterNetwork(undefined); setFilterAddress(undefined) }}
                  aria-label="Clear network filter"
                >×</button>
              </span>
            )}
          </div>
          <div className={styles.sectionActions}>
            {/* Account filter — scoped to selected network */}
            <select
              className={styles.accountSelect}
              value={filterAddress ?? ''}
              onChange={(e) => setFilterAddress(e.target.value || undefined)}
            >
              <option value="">All accounts</option>
              {filteredAccounts.map((acc) => (
                <option key={acc.address} value={acc.address}>
                  {acc.chainName
                    ? `${acc.chainName} · ${acc.address.slice(0, 6)}…${acc.address.slice(-4)}`
                    : `${acc.address.slice(0, 8)}…${acc.address.slice(-4)}`}
                </option>
              ))}
            </select>

            {/* Sort order */}
            <select
              className={styles.accountSelect}
              value={sortOrder}
              onChange={(e) => setSortOrder(e.target.value as SortOrder)}
            >
              <option value="newest">Newest first</option>
              <option value="oldest">Oldest first</option>
              <option value="amount-desc">Amount ↓</option>
              <option value="amount-asc">Amount ↑</option>
            </select>

            <button className="btn btn-sm btn-ghost" onClick={() => navigate('/activity')}>
              View all
            </button>
          </div>
        </div>

        {!displayTxs.length ? (
          <p className={styles.noTx}>No transactions yet.</p>
        ) : (
          <div className={styles.txList}>
            {displayTxs.slice(0, 5).map((tx) => (
              <TxRow key={tx.id} tx={tx} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
