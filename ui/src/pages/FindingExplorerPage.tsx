import { useState } from 'react'
import {
  Box,
  Typography,
  TextField,
  Button,
  Paper,
  CircularProgress,
} from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { explanationApi } from '../services/api'

function FindingExplorerPage() {
  const [findingId, setFindingId] = useState('')
  const [explanation, setExplanation] = useState<any>(null)

  const mutation = useMutation({
    mutationFn: explanationApi.explainFinding,
    onSuccess: (data) => {
      setExplanation(data)
    },
  })

  const handleExplain = () => {
    if (findingId.trim()) {
      mutation.mutate(findingId)
    }
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Finding Explorer
      </Typography>

      <Paper sx={{ p: 2, mb: 3 }}>
        <TextField
          fullWidth
          label="Finding ID"
          value={findingId}
          onChange={(e) => setFindingId(e.target.value)}
          placeholder="Enter finding UUID"
          sx={{ mb: 2 }}
        />
        <Button
          variant="contained"
          onClick={handleExplain}
          disabled={!findingId.trim() || mutation.isPending}
        >
          {mutation.isPending ? <CircularProgress size={20} /> : 'Explain Finding'}
        </Button>
      </Paper>

      {mutation.isError && (
        <Paper sx={{ p: 2, mb: 2, bgcolor: 'error.light' }}>
          <Typography color="error">
            Error: {mutation.error instanceof Error ? mutation.error.message : 'Unknown error'}
          </Typography>
        </Paper>
      )}

      {explanation && (
        <Paper sx={{ p: 2 }}>
          <Typography variant="h6" gutterBottom>
            Explanation
          </Typography>
          <Typography variant="body1" sx={{ mb: 2, whiteSpace: 'pre-wrap' }}>
            {explanation.explanation}
          </Typography>
          
          {explanation.stepsToReproduce && (
            <Box sx={{ mb: 2 }}>
              <Typography variant="subtitle1" gutterBottom>
                Steps to Reproduce:
              </Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                {explanation.stepsToReproduce}
              </Typography>
            </Box>
          )}
          
          {explanation.codePointers && (
            <Box sx={{ mb: 2 }}>
              <Typography variant="subtitle1" gutterBottom>
                Code Pointers:
              </Typography>
              <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                {explanation.codePointers}
              </Typography>
            </Box>
          )}
          
          {explanation.recommendedFix && (
            <Box>
              <Typography variant="subtitle1" gutterBottom>
                Recommended Fix:
              </Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                {explanation.recommendedFix}
              </Typography>
            </Box>
          )}
        </Paper>
      )}
    </Box>
  )
}

export default FindingExplorerPage

