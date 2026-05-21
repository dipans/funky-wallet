import { useState } from 'react'
import { useAllTransactions } from '../../hooks/useWallet'
import type { TransactionStatus, Transaction } from '../../types'
import { explorerTxUrl } from '../../utils/explorer'
import styles from './Activity.module.css'

const STATUS_FILTERS: (TransactionStatus | 'ALL')[] = ['ALL', 'PENDING', 'CONFIRMED', 'RECEIVED', 'FAILED']

function TxCard({ tx }: { tx: Transaction }) {
  const isOut = tx.status !== 'RECEIVED'
  return (
    <div className={`card ${styles.txCard} fade-in`}>
      <div className={styles.txHeader}>
        <span className={`${styles.dir} ${isOut ? styles.out : styles.in}`}>
          {isOut ? '↑ Sent' : '↓ Received'}
        </span>
        <span className={`badge badge-${tx.status.toLowerCase()}`}>{tx.status}</span>
      </div>
      <div className={styles.txAmount}>
        {isOut ? '-' : '+'}{tx.amount} <span className={styles.symbol}>{tx.symbol}</span>
      </div>
      <div className={styles.txMeta}>
        <span className="mono">{isOut ? tx.toAddress : tx.fromAddress}</span>
      </div>
      <div className={styles.txFooter}>
        <span>{new Date(tx.createdAt).toLocaleString()}</span>
        {tx.hash && (
          <a
            href={explorerTxUrl(tx.hash, tx.network)}
            target="_blank"
            rel="noopener noreferrer"
            className={`mono ${styles.hash} ${styles.explorerLink}`}
            title={tx.hash}
          >
            ↗ Explorer
          </a>
        )}
      </div>
    </div>
  )
}

export default function Activity() {
  const [filter, setFilter] = useState<TransactionStatus | 'ALL'>('ALL')
  const { data: transactions, isLoading } = useAllTransactions()

  const filtered = transactions?.filter(
    (tx) => filter === 'ALL' || tx.status === filter
  ) ?? []

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Activity</h1>
      <div className={styles.filters}>
        {STATUS_FILTERS.map((s) => (
          <button
            key={s}
            className={`btn btn-sm ${filter === s ? styles.filterActive : 'btn-ghost'}`}
            onClick={() => setFilter(s)}
          >
            {s}
          </button>
        ))}
      </div>
      {isLoading && <div className="spinner" style={{ margin: '40px auto' }} />}
      {!isLoading && !filtered.length && (
        <p className={styles.empty}>No transactions found.</p>
      )}
      <div className={styles.list}>
        {filtered.map((tx) => (
          <TxCard key={tx.id} tx={tx} />
        ))}
      </div>
    </div>
  )
}
