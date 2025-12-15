import { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import {
  AppBar,
  Toolbar,
  Typography,
  Container,
  Box,
  Tabs,
  Tab,
} from '@mui/material'
import SecurityIcon from '@mui/icons-material/Security'

interface LayoutProps {
  children: ReactNode
}

function Layout({ children }: LayoutProps) {
  const location = useLocation()
  
  const getTabValue = () => {
    if (location.pathname === '/') return 0
    if (location.pathname.startsWith('/query')) return 1
    if (location.pathname.startsWith('/knowledge')) return 2
    if (location.pathname.startsWith('/policy')) return 3
    if (location.pathname.startsWith('/findings')) return 4
    return 0
  }

  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static">
        <Toolbar>
          <SecurityIcon sx={{ mr: 2 }} />
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            Temporal Security Analyst
          </Typography>
        </Toolbar>
        <Tabs value={getTabValue()} textColor="inherit" indicatorColor="secondary">
          <Tab label="Dashboard" component={Link} to="/" />
          <Tab label="Query" component={Link} to="/query" />
          <Tab label="Knowledge Base" component={Link} to="/knowledge" />
          <Tab label="Policies" component={Link} to="/policy" />
          <Tab label="Findings" component={Link} to="/findings" />
        </Tabs>
      </AppBar>
      {children}
    </Box>
  )
}

export default Layout

