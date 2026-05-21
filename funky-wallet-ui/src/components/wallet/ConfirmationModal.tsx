import type { Environment } from '../../types'
import { ENV_COLORS } from '../../utils/chain'
import styles from './ConfirmationModal.module.css'

interface Props {
  open: boolean
  fromAddress: string
  toAddress: string
  amount: string
  chainName: string
  environment: Environment
  onConfirm: () => void
  onCancel: () => void
  isSubmitting: boolean
}

export default function ConfirmationModal({
  open,
  fromAddress,
  toAddress,
  amount,
  chainName,
  environment,
  onConfirm,
  onCancel,
  isSubmitting,
}: Props) {
  if (!open) return null

  const envColor = ENV_COLORS[environment]
  const isMainnet = environment === 'MAINNET'

  return (
    <div className={styles.backdrop} onClick={onCancel}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <h2 className={styles.title}>Confirm Transaction</h2>

        <div className={styles.envRow}>
          <span className={styles.envLabel}>Network</span>
          <span
            className={styles.envBadge}
            style={{ background: envColor.bg, color: envColor.text }}
          >
            {chainName}
          </span>
          <span
            className={styles.envBadge}
            style={{ background: envColor.bg, color: envColor.text }}
          >
            {environment}
          </span>
        </div>

        {isMainnet ? (
          <div className={styles.warningMainnet}>
            ⚠ This is a real mainnet transaction. Funds are at risk.
          </div>
        ) : (
          <div className={styles.infoSoft}>
            This transaction will be submitted to the {environment.toLowerCase()} environment.
          </div>
        )}

        <div className={styles.summary}>
          <div className={styles.summaryRow}>
            <span className={styles.summaryLabel}>From</span>
            <span className={styles.summaryValue}>
              {fromAddress.slice(0, 10)}…{fromAddress.slice(-6)}
            </span>
          </div>
          <div className={styles.summaryRow}>
            <span className={styles.summaryLabel}>To</span>
            <span className={styles.summaryValue}>
              {toAddress.slice(0, 10)}…{toAddress.slice(-6)}
            </span>
          </div>
          <div className={styles.summaryRow}>
            <span className={styles.summaryLabel}>Amount</span>
            <span className={styles.summaryValue}>{amount}</span>
          </div>
          <div className={styles.summaryRow}>
            <span className={styles.summaryLabel}>Chain</span>
            <span className={styles.summaryValue}>{chainName}</span>
          </div>
        </div>

        <div className={styles.actions}>
          <button className="btn btn-ghost" onClick={onCancel} disabled={isSubmitting}>
            Cancel
          </button>
          <button
            className={isMainnet ? `btn ${styles.btnDanger}` : 'btn btn-primary'}
            onClick={onConfirm}
            disabled={isSubmitting}
          >
            {isSubmitting ? <span className="spinner" /> : 'Sign & Send'}
          </button>
        </div>
      </div>
    </div>
  )
}
