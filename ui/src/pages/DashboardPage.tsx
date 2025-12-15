import { Box, Typography, Paper, Grid } from '@mui/material'
import QueryIcon from '@mui/icons-material/QueryBuilder'
import BookIcon from '@mui/icons-material/Book'
import PolicyIcon from '@mui/icons-material/Gavel'
import BugReportIcon from '@mui/icons-material/BugReport'

function DashboardPage() {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Dashboard
      </Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, textAlign: 'center' }}>
            <QueryIcon sx={{ fontSize: 48, color: 'primary.main', mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              Natural Language Queries
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Ask questions about scans, findings, and security metrics
            </Typography>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, textAlign: 'center' }}>
            <BookIcon sx={{ fontSize: 48, color: 'secondary.main', mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              Knowledge Base
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Manage organizational Q&A pairs for common questions
            </Typography>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, textAlign: 'center' }}>
            <PolicyIcon sx={{ fontSize: 48, color: 'success.main', mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              Policy Management
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Create and compile natural language security policies
            </Typography>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, textAlign: 'center' }}>
            <BugReportIcon sx={{ fontSize: 48, color: 'warning.main', mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              Finding Explorer
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Explore and understand security findings in detail
            </Typography>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  )
}

export default DashboardPage

