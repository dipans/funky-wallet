import { useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { useWalletStore } from '../../store/walletStore'
import styles from './Receive.module.css'

export default function Receive() {
  const activeAccount = useWalletStore((s) => s.activeAccount)
  const [copied, setCopied] = useState(false)

  const copy = () => {
    if (!activeAccount) return
    navigator.clipboard.writeText(activeAccount.address)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  if (!activeAccount) {
    return <p className={styles.empty}>No account selected.</p>
  }

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Receive</h1>
      <div className={`card ${styles.card}`}>
        <p className={styles.network}>{activeAccount.network}</p>
        <div className={styles.qr}>
          <QRCodeSVG
            value={activeAccount.address}
            size={200}
            bgColor="#111111"
            fgColor="#c8f135"
            level="M"
          />
        </div>
        <p className={styles.address}>{activeAccount.address}</p>
        <button className="btn btn-primary btn-full" onClick={copy}>
          {copied ? 'Copied!' : 'Copy Address'}
        </button>
      </div>
    </div>
  )
}
