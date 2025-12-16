import { useState } from 'react'
import {
  Box,
  TextField,
  Button,
  Paper,
  Typography,
  CircularProgress,
  Chip,
} from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { queryApi, QueryResponse } from '../services/api'
import SendIcon from '@mui/icons-material/Send'

interface QueryHistoryEntry {
  query: string
  team?: string
  response: QueryResponse
  timestamp: Date
}

function QueryPage() {
  const [query, setQuery] = useState('')
  const [team, setTeam] = useState('')
  const [history, setHistory] = useState<QueryHistoryEntry[]>([])

  const mutation = useMutation({
    mutationFn: (q: string) => queryApi.processQuery({ query: q, team }),
    onSuccess: (data, variables) => {
      setHistory([{
        query: variables,
        team: team || undefined,
        response: data,
        timestamp: new Date()
      }, ...history])
      setQuery('')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (query.trim()) {
      mutation.mutate(query)
    }
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Natural Language Query
      </Typography>
      
      <Paper sx={{ p: 2, mb: 3 }}>
        <form onSubmit={handleSubmit}>
          <TextField
            fullWidth
            label="Team (optional)"
            value={team}
            onChange={(e) => setTeam(e.target.value)}
            sx={{ mb: 2 }}
          />
          <TextField
            fullWidth
            multiline
            rows={3}
            label="Ask a question"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="e.g., Why did scan X take 47 minutes?"
            sx={{ mb: 2 }}
          />
          <Button
            type="submit"
            variant="contained"
            endIcon={mutation.isPending ? <CircularProgress size={20} /> : <SendIcon />}
            disabled={!query.trim() || mutation.isPending}
          >
            Submit Query
          </Button>
        </form>
      </Paper>

      {mutation.isError && (
        <Paper sx={{ p: 2, mb: 2, bgcolor: 'error.light' }}>
          <Typography color="error">
            Error: {mutation.error instanceof Error ? mutation.error.message : 'Unknown error'}
          </Typography>
        </Paper>
      )}

      {history.length > 0 && (
        <Box>
          <Typography variant="h6" gutterBottom>
            Query History
          </Typography>
          {history.map((entry, idx) => (
            <Paper key={idx} sx={{ p: 2, mb: 2 }}>
              <Box sx={{ mb: 2 }}>
                <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                  Question:
                </Typography>
                <Typography variant="body2" sx={{ 
                  fontStyle: 'italic', 
                  color: 'primary.main',
                  mb: 2,
                  p: 1,
                  bgcolor: 'action.hover',
                  borderRadius: 1
                }}>
                  {entry.query}
                </Typography>
                {entry.team && (
                  <Chip 
                    label={`Team: ${entry.team}`} 
                    size="small" 
                    sx={{ mb: 1, mr: 1 }}
                  />
                )}
                <Chip 
                  label={new Date(entry.timestamp).toLocaleString()} 
                  size="small" 
                  variant="outlined"
                  sx={{ mb: 1 }}
                />
              </Box>
              <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
                <Chip label={entry.response.source} size="small" />
                <Chip label={`Confidence: ${(entry.response.confidence * 100).toFixed(0)}%`} size="small" />
              </Box>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Answer:
              </Typography>
              <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
                {entry.response.answer}
              </Typography>
            </Paper>
          ))}
        </Box>
      )}
    </Box>
  )
}

export default QueryPage

