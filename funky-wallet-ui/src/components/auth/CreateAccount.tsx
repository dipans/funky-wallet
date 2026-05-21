import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCreateAccount } from '../../hooks/useWallet'
import { SUPPORTED_CHAINS, ENV_COLORS } from '../../utils/chain'
import type { ChainInfo } from '../../utils/chain'
import type { NetworkType } from '../../types'
import styles from './CreateAccount.module.css'

const NETWORK_FAMILIES: NetworkType[] = ['EVM', 'SOLANA', 'BITCOIN']

type Step = 'family' | 'chain' | 'mnemonic' | 'confirm'

export default function CreateAccount() {
  const navigate = useNavigate()
  const { mutateAsync, isPending } = useCreateAccount()

  const [step, setStep] = useState<Step>('family')
  const [selectedFamily, setSelectedFamily] = useState<NetworkType>('EVM')
  const [selectedChain, setSelectedChain] = useState<ChainInfo | null>(null)
  const [mnemonic, setMnemonic] = useState('')
  const [revealed, setRevealed] = useState(false)
  const [checks, setChecks] = useState({ written: false, nobody: false, lost: false })

  const chainsForFamily = SUPPORTED_CHAINS.filter((c) => c.networkType === selectedFamily)

  const handleCreate = async () => {
    if (!selectedChain) return
    const result = await mutateAsync({
      network: selectedChain.networkType === 'EVM' ? 'ETHEREUM'
             : selectedChain.networkType === 'SOLANA' ? 'SOLANA' : 'BITCOIN',
      chainId: selectedChain.chainId,
      chainName: selectedChain.name,
      networkType: selectedChain.networkType,
    })
    setMnemonic(result.mnemonic)
    setStep('mnemonic')
  }

  const handleFinish = () => {
    setMnemonic('')
    navigate('/')
  }

  const allChecked = Object.values(checks).every(Boolean)

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Create Account</h1>

      {/* Step 1 — Pick network family */}
      {step === 'family' && (
        <div className={`card ${styles.card} fade-in`}>
          <p className={styles.sub}>Choose a blockchain.</p>
          <div className={styles.networkGrid}>
            {NETWORK_FAMILIES.map((f) => (
              <button
                key={f}
                className={`${styles.networkBtn} ${selectedFamily === f ? styles.networkBtnActive : ''}`}
                onClick={() => setSelectedFamily(f)}
              >
                {f}
              </button>
            ))}
          </div>
          <button
            className="btn btn-primary btn-full"
            onClick={() => { setSelectedChain(null); setStep('chain') }}
          >
            Next →
          </button>
        </div>
      )}

      {/* Step 2 — Pick specific chain */}
      {step === 'chain' && (
        <div className={`card ${styles.card} fade-in`}>
          <p className={styles.sub}>Choose a network.</p>
          <div className={styles.chainList}>
            {chainsForFamily.map((chain) => {
              const envColor = ENV_COLORS[chain.environment]
              const isSelected = selectedChain?.chainId === chain.chainId
              return (
                <button
                  key={chain.chainId}
                  className={`${styles.chainBtn} ${isSelected ? styles.chainBtnActive : ''}`}
                  onClick={() => setSelectedChain(chain)}
                >
                  <span className={styles.chainBtnName}>{chain.name}</span>
                  <span
                    className={styles.envBadge}
                    style={{ background: envColor.bg, color: envColor.text }}
                  >
                    {chain.environment}
                  </span>
                </button>
              )
            })}
          </div>
          <div className={styles.chainActions}>
            <button className="btn btn-ghost" onClick={() => setStep('family')}>
              ← Back
            </button>
            <button
              className="btn btn-primary"
              onClick={handleCreate}
              disabled={!selectedChain || isPending}
            >
              {isPending ? <span className="spinner" /> : 'Generate Account'}
            </button>
          </div>
        </div>
      )}

      {/* Step 3 — Mnemonic reveal */}
      {step === 'mnemonic' && (
        <div className={`card ${styles.card} fade-in`}>
          <p className={styles.sub}>
            Your secret recovery phrase. <strong>Store it somewhere safe — it will never be shown again.</strong>
          </p>
          <div
            className={`${styles.mnemonicBox} ${revealed ? '' : styles.mnemonicBlurred}`}
            onClick={() => setRevealed(true)}
          >
            {!revealed && <span className={styles.revealHint}>Click to reveal</span>}
            <p className={styles.mnemonicWords}>{mnemonic}</p>
          </div>
          {!revealed && <p className={styles.wordCount}>({mnemonic.split(' ').length} words)</p>}
          <button className="btn btn-primary btn-full" onClick={() => setStep('confirm')} disabled={!revealed}>
            I&apos;ve saved my phrase →
          </button>
        </div>
      )}

      {/* Step 4 — Confirm checklist */}
      {step === 'confirm' && (
        <div className={`card ${styles.card} fade-in`}>
          <p className={styles.sub}>Before you continue, confirm the following:</p>
          <div className={styles.checklist}>
            {([
              ['written', "I've written down my secret phrase"],
              ['nobody',  "I haven't shown it to anyone"],
              ['lost',    "I understand that if I lose it, I lose access"],
            ] as [keyof typeof checks, string][]).map(([key, label]) => (
              <label key={key} className={styles.checkItem}>
                <input
                  type="checkbox"
                  checked={checks[key]}
                  onChange={(e) => setChecks((c) => ({ ...c, [key]: e.target.checked }))}
                />
                {label}
              </label>
            ))}
          </div>
          <button className="btn btn-primary btn-full" onClick={handleFinish} disabled={!allChecked}>
            Go to Dashboard
          </button>
        </div>
      )}
    </div>
  )
}
