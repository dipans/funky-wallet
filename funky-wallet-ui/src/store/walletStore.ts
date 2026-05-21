import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { Account, Network } from '../types'

interface ConfirmationSettings {
  mainnet: true        // always true, not changeable
  testnet: boolean
  devnet: boolean
  local: boolean
}

interface WalletState {
  accounts: Account[]
  activeAccount: Account | null
  activeNetwork: Network
  confirmationSettings: ConfirmationSettings
  setAccounts: (accounts: Account[]) => void
  addAccount: (account: Account) => void
  setActiveAccount: (account: Account) => void
  setActiveNetwork: (network: Network) => void
  setConfirmationSettings: (s: Partial<Omit<ConfirmationSettings, 'mainnet'>>) => void
  reset: () => void
}

export const useWalletStore = create<WalletState>()(
  persist(
    (set) => ({
      accounts: [],
      activeAccount: null,
      activeNetwork: 'ETHEREUM',
      confirmationSettings: { mainnet: true, testnet: false, devnet: false, local: false },
      setAccounts: (accounts) => set({ accounts }),
      addAccount: (account) =>
        set((s) => ({
          accounts: [...s.accounts, account],
          activeAccount: s.activeAccount ?? account,
        })),
      setActiveAccount: (account) => set({ activeAccount: account }),
      setActiveNetwork: (network) => set({ activeNetwork: network }),
      setConfirmationSettings: (s) =>
        set((state) => ({
          confirmationSettings: { ...state.confirmationSettings, ...s },
        })),
      reset: () => set({ accounts: [], activeAccount: null }),
    }),
    {
      name: 'fw-wallet',
      // accounts are auth-scoped and always fetched from API — never persist them
      // mnemonic is never in store — only in component local state
      partialize: (s) => ({
        activeNetwork: s.activeNetwork,
        confirmationSettings: s.confirmationSettings,
      }),
    }
  )
)

// Seed test account on first load in dev if no accounts exist
const testAddress = import.meta.env.VITE_TEST_ACCOUNT_ADDRESS
if (testAddress && useWalletStore.getState().accounts.length === 0) {
  useWalletStore.getState().addAccount({
    id: '00000000-0000-0000-0000-000000000002',
    address: testAddress,
    publicKey: '0x',
    network: 'ETHEREUM',
    networkType: 'EVM',
    chainId: 560048,
    chainName: 'Ethereum Hoodi',
    environment: 'TESTNET',
    createdAt: new Date().toISOString(),
  })
}
