import { useState } from 'react'
import {
  Box,
  Typography,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
} from '@mui/material'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { knowledgeBaseApi } from '../services/api'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'

function KnowledgeBasePage() {
  const [open, setOpen] = useState(false)
  const [question, setQuestion] = useState('')
  const [answer, setAnswer] = useState('')
  const [team, setTeam] = useState('')
  const queryClient = useQueryClient()

  const { data: entries, isLoading } = useQuery({
    queryKey: ['knowledgeBase'],
    queryFn: () => knowledgeBaseApi.list(),
  })

  const createMutation = useMutation({
    mutationFn: knowledgeBaseApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['knowledgeBase'] })
      setOpen(false)
      setQuestion('')
      setAnswer('')
      setTeam('')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: knowledgeBaseApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['knowledgeBase'] })
    },
  })

  const handleCreate = () => {
    createMutation.mutate({
      question,
      answer,
      createdBy: 'admin', // In production, get from auth context
      team: team || undefined,
    })
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Typography variant="h4">Knowledge Base</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setOpen(true)}
        >
          Add Q&A Pair
        </Button>
      </Box>

      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Add Knowledge Base Entry</DialogTitle>
        <DialogContent>
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
            label="Question"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            sx={{ mb: 2 }}
          />
          <TextField
            fullWidth
            multiline
            rows={5}
            label="Answer"
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button onClick={handleCreate} variant="contained" disabled={!question || !answer}>
            Create
          </Button>
        </DialogActions>
      </Dialog>

      {isLoading ? (
        <Typography>Loading...</Typography>
      ) : (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Question</TableCell>
                <TableCell>Answer</TableCell>
                <TableCell>Team</TableCell>
                <TableCell>Usage</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {entries?.map((entry) => (
                <TableRow key={entry.kbId}>
                  <TableCell>{entry.question}</TableCell>
                  <TableCell>{entry.answer.substring(0, 100)}...</TableCell>
                  <TableCell>{entry.team || '-'}</TableCell>
                  <TableCell>{entry.usageCount}</TableCell>
                  <TableCell>
                    <IconButton
                      onClick={() => deleteMutation.mutate(entry.kbId)}
                      color="error"
                    >
                      <DeleteIcon />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  )
}

export default KnowledgeBasePage

