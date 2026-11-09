import SearchIcon from '@mui/icons-material/Search'
import { Autocomplete, Box, InputAdornment, TextField, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { SearchHit } from '../api/types'

export function GlobalSearch() {
  const [input, setInput] = useState('')
  const [term, setTerm] = useState('')
  const navigate = useNavigate()

  useEffect(() => {
    const t = setTimeout(() => setTerm(input.trim()), 250)
    return () => clearTimeout(t)
  }, [input])

  const { data = [], isFetching } = useQuery({
    queryKey: ['search', term],
    queryFn: () => api.get<SearchHit[]>('/trades/search', { params: { q: term } }).then((r) => r.data),
    enabled: term.length >= 2,
  })

  return (
    <Autocomplete
      size="small"
      options={term.length >= 2 ? data : []}
      loading={isFetching}
      filterOptions={(x) => x}
      getOptionLabel={(o) => (typeof o === 'string' ? o : o.tradeRef)}
      inputValue={input}
      onInputChange={(_, v) => setInput(v)}
      onChange={(_, v) => {
        if (v && typeof v !== 'string') {
          navigate(`/trades/${v.tradeId}`)
          setInput('')
        }
      }}
      noOptionsText={term.length < 2 ? 'Type a trade ref, FO ref or counterparty' : 'No matches'}
      sx={{ width: { xs: 180, sm: 320, md: 380 } }}
      renderOption={(props, o) => {
        const { key, ...rest } = props as typeof props & { key: string }
        return (
          <Box component="li" key={key} {...rest} sx={{ display: 'block !important' }}>
            <Typography variant="body2" className="mono" sx={{ fontWeight: 600 }}>{o.tradeRef}</Typography>
            <Typography variant="caption" color="text.secondary">
              {o.counterparty} · {o.product} {o.currencyPair} · {o.externalRef}
            </Typography>
          </Box>
        )
      }}
      renderInput={(params) => (
        <TextField
          {...params}
          placeholder="Search trades…"
          slotProps={{
            ...params.slotProps,
            input: {
              ...params.slotProps.input,
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
          sx={{ '& .MuiOutlinedInput-root': { bgcolor: 'background.default' } }}
        />
      )}
    />
  )
}
