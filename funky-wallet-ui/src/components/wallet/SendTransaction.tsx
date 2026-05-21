import { useState, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useWalletStore } from '../../store/walletStore'
import { useSendTransaction } from '../../hooks/useWallet'
import ConfirmationModal from './ConfirmationModal'
import type { Environment } from '../../types'
import styles from './SendTransaction.module.css'

export default function SendTransaction() {
  const navigate = useNavigate()
  const location = useLocation()
  const accounts = useWalletStore((s) => s.accounts)
  const activeAccount = useWalletStore((s) => s.activeAccount)
  const setActiveAccount = useWalletStore((s) => s.setActiveAccount)
  const confirmationSettings = useWalletStore((s) => s.confirmationSettings)

  // fromAddress only set when navigated from an account card's Send button
  const preselectedAddress = (location.state as { fromAddress?: string } | null)?.fromAddress
  const fromAccount = preselectedAddress
    ? accounts.find((a) => a.address === preselectedAddress) ?? null
    : null

  const [fromAddressInput, setFromAddressInput] = useState('')
  const effectiveFromAddress = preselectedAddress ?? fromAddressInput

  useEffect(() => {
    if (fromAccount && fromAccount.id !== activeAccount?.id) {
      setActiveAccount(fromAccount)
    }
  }, [fromAccount?.id])
  const { mutateAsync, isPending } = useSendTransaction()

  const [toAddress, setToAddress]   = useState('')
  const [amount, setAmount]         = useState('')
  const [error, setError]           = useState('')
  const [txId, setTxId]             = useState('')
  const [showConfirm, setShowConfirm] = useState(false)

  const needsConfirmation = (env: Environment): boolean => {
    if (env === 'MAINNET') return true
    if (env === 'TESTNET') return confirmationSettings.testnet
    if (env === 'DEVNET')  return confirmationSettings.devnet
    return confirmationSettings.local
  }

  const resolvedNetwork = fromAccount?.network ?? 'ETHEREUM'
  const resolvedEnvironment = fromAccount?.environment ?? 'LOCAL'
  const resolvedChainName = fromAccount?.chainName ?? ''

  const doSend = async () => {
    if (!effectiveFromAddress) return
    setError('')
    setShowConfirm(false)
    try {
      const tx = await mutateAsync({
        fromAddress: effectiveFromAddress,
        toAddress,
        amount,
        network: resolvedNetwork,
      })
      setTxId(tx.id)
    } catch (err: unknown) {
      setError((err as { message?: string })?.message ?? 'Transaction failed')
    }
  }

  const handleSend = () => {
    if (!effectiveFromAddress) return
    if (needsConfirmation(resolvedEnvironment)) {
      setShowConfirm(true)
    } else {
      doSend()
    }
  }

  if (txId) {
    return (
      <div className={styles.page}>
        <div className={`card ${styles.successCard} fade-in`}>
          <p className={styles.successIcon}>✓</p>
          <h2 className={styles.successTitle}>Transaction Submitted</h2>
          <p className={styles.successSub}>Your transaction is being processed.</p>
          <button className="btn btn-primary btn-full" onClick={() => navigate('/activity')}>
            View Activity
          </button>
          <button className="btn btn-ghost btn-full" onClick={() => navigate('/')}>
            Back to Dashboard
          </button>
        </div>
      </div>
    )
  }

  return (
    <>
      <div className={styles.page}>
        <h1 className={styles.title}>Send</h1>
        <div className={`card ${styles.card}`}>
          {preselectedAddress && fromAccount ? (
            <div className={styles.fromBox}>
              <span className={styles.fromLabel}>From</span>
              <span className={styles.fromChain}>{fromAccount.chainName}</span>
              <span className={styles.fromAddress}>{fromAccount.address}</span>
            </div>
          ) : (
            <div>
              <label className="input-label">From Address</label>
              <input
                className="input-field"
                placeholder="0x..."
                value={fromAddressInput}
                onChange={(e) => setFromAddressInput(e.target.value)}
              />
            </div>
          )}
          <div>
            <label className="input-label">Recipient Address</label>
            <input
              className="input-field"
              placeholder="0x..."
              value={toAddress}
              onChange={(e) => setToAddress(e.target.value)}
            />
          </div>
          <div>
            <label className="input-label">Amount</label>
            <input
              className="input-field"
              placeholder="0.00"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              type="number"
              min="0"
            />
          </div>
          {error && <p className={styles.error}>{error}</p>}
          <button
            className="btn btn-primary btn-full"
            onClick={handleSend}
            disabled={isPending || !toAddress || !amount || !effectiveFromAddress}
          >
            {isPending ? <><span className="spinner" /> Signing…</> : 'Send Transaction'}
          </button>
          <button className="btn btn-ghost btn-full" onClick={() => navigate('/')}>Cancel</button>
        </div>
      </div>

      {effectiveFromAddress && (
        <ConfirmationModal
          open={showConfirm}
          fromAddress={effectiveFromAddress}
          toAddress={toAddress}
          amount={amount}
          chainName={resolvedChainName}
          environment={resolvedEnvironment}
          onConfirm={doSend}
          onCancel={() => setShowConfirm(false)}
          isSubmitting={isPending}
        />
      )}
    </>
  )
}
