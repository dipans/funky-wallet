import { useState, useEffect } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import type { Account } from '../../types'
import styles from './ReceiveModal.module.css'

interface Props {
  account: Account
  onClose: () => void
}

export default function ReceiveModal({ account, onClose }: Props) {
  const [copied, setCopied] = useState(false)

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onClose])

  const copy = () => {
    navigator.clipboard.writeText(account.address)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div className={styles.backdrop} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <span className={styles.title}>Receive — {account.chainName}</span>
          <button className={styles.close} onClick={onClose}>✕</button>
        </div>
        <div className={styles.qr}>
          <QRCodeSVG
            value={account.address}
            size={200}
            bgColor="#111111"
            fgColor="#c8f135"
            level="M"
          />
        </div>
        <p className={styles.address}>{account.address}</p>
        <button className="btn btn-primary btn-full" onClick={copy}>
          {copied ? 'Copied!' : 'Copy Address'}
        </button>
      </div>
    </div>
  )
}
