import { Routes, Route } from 'react-router-dom'
import { Container } from '@mui/material'
import Layout from './components/Layout'
import QueryPage from './pages/QueryPage'
import KnowledgeBasePage from './pages/KnowledgeBasePage'
import PolicyPage from './pages/PolicyPage'
import FindingExplorerPage from './pages/FindingExplorerPage'
import DashboardPage from './pages/DashboardPage'

function App() {
  return (
    <Layout>
      <Container maxWidth="xl" sx={{ mt: 4, mb: 4 }}>
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/query" element={<QueryPage />} />
          <Route path="/knowledge" element={<KnowledgeBasePage />} />
          <Route path="/policy" element={<PolicyPage />} />
          <Route path="/findings" element={<FindingExplorerPage />} />
        </Routes>
      </Container>
    </Layout>
  )
}

export default App

