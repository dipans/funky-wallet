import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { accountApi, transactionApi } from '../api'
import type { CreateAccountPayload } from '../api'
import { useWalletStore } from '../store/walletStore'

export function useAccounts() {
  const setAccounts = useWalletStore((s) => s.setAccounts)
  const setActiveAccount = useWalletStore((s) => s.setActiveAccount)
  const activeAccount = useWalletStore((s) => s.activeAccount)
  return useQuery({
    queryKey: ['accounts'],
    queryFn: async () => {
      const accounts = await accountApi.list()
      setAccounts(accounts)
      // Auto-select first account if current active is no longer in the list
      if (accounts.length > 0 && (!activeAccount || !accounts.find((a) => a.id === activeAccount.id))) {
        setActiveAccount(accounts[0])
      }
      return accounts
    },
  })
}

export function useCreateAccount() {
  const addAccount = useWalletStore((s) => s.addAccount)
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateAccountPayload) => accountApi.create(payload),
    onSuccess: (data) => {
      addAccount(data.account)
      qc.invalidateQueries({ queryKey: ['accounts'] })
    },
  })
}

export function useBalance(address: string | undefined) {
  return useQuery({
    queryKey: ['balance', address],
    queryFn: () => accountApi.getBalance(address!),
    enabled: !!address,
    refetchInterval: 30_000,
  })
}

export function useTransactions(address?: string) {
  return useQuery({
    queryKey: ['transactions', address ?? 'all'],
    queryFn: () => transactionApi.list(address),
    refetchInterval: (query) => {
      const hasPending = query.state.data?.some((tx) => tx.status === 'PENDING')
      return hasPending ? 5_000 : 15_000
    },
  })
}

export function useAllTransactions() {
  return useQuery({
    queryKey: ['transactions', 'all'],
    queryFn: () => transactionApi.list(),
    refetchInterval: (query) => {
      const txs = query.state.data
      const hasPending = txs?.some((tx) => tx.status === 'PENDING')
      return hasPending ? 5_000 : 15_000
    },
  })
}

export function useTransactionStatus(txId: string | undefined) {
  return useQuery({
    queryKey: ['transaction', txId],
    queryFn: () => transactionApi.get(txId!),
    enabled: !!txId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'PENDING' ? 5_000 : false
    },
  })
}

export function useSendTransaction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: transactionApi.send,
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ['transactions', variables.fromAddress] })
      qc.invalidateQueries({ queryKey: ['balance', variables.fromAddress] })
    },
  })
}
