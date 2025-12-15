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
import { policyApi } from '../services/api'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism'

function PolicyPage() {
  const [policy, setPolicy] = useState('')
  const [compiled, setCompiled] = useState<any>(null)

  const mutation = useMutation({
    mutationFn: policyApi.compile,
    onSuccess: (data) => {
      setCompiled(data)
    },
  })

  const handleCompile = () => {
    if (policy.trim()) {
      mutation.mutate(policy)
    }
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Policy Compiler
      </Typography>

      <Paper sx={{ p: 2, mb: 3 }}>
        <TextField
          fullWidth
          multiline
          rows={4}
          label="Natural Language Policy"
          value={policy}
          onChange={(e) => setPolicy(e.target.value)}
          placeholder='e.g., "block builds if reachable critical vuln in prod dependency"'
          sx={{ mb: 2 }}
        />
        <Button
          variant="contained"
          onClick={handleCompile}
          disabled={!policy.trim() || mutation.isPending}
        >
          {mutation.isPending ? <CircularProgress size={20} /> : 'Compile Policy'}
        </Button>
      </Paper>

      {mutation.isError && (
        <Paper sx={{ p: 2, mb: 2, bgcolor: 'error.light' }}>
          <Typography color="error">
            Error: {mutation.error instanceof Error ? mutation.error.message : 'Unknown error'}
          </Typography>
        </Paper>
      )}

      {compiled && (
        <Paper sx={{ p: 2 }}>
          <Typography variant="h6" gutterBottom>
            Compiled Policy
          </Typography>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            Action: {compiled.action} | Condition: {compiled.condition} | Scope: {compiled.scope}
          </Typography>
          <SyntaxHighlighter language="javascript" style={vscDarkPlus}>
            {compiled.ruleCode}
          </SyntaxHighlighter>
        </Paper>
      )}
    </Box>
  )
}

export default PolicyPage

