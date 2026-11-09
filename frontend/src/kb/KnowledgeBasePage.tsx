import ArticleOutlinedIcon from '@mui/icons-material/ArticleOutlined'
import SearchIcon from '@mui/icons-material/Search'
import {
  Box, Chip, InputAdornment, Link, List, ListItemButton, ListSubheader, Paper, TextField, Typography,
} from '@mui/material'
import { useMemo, useState } from 'react'
import { Link as RouterLink, useLocation, useParams } from 'react-router-dom'
import { PageHeader } from '../components/PageHeader'
import { ARTICLES, CATEGORIES, GLOSSARY } from './content'
import { Markdown } from './Markdown'

export default function KnowledgeBasePage() {
  const { slug } = useParams()
  const base = useLocation().pathname.startsWith('/portal') ? '/portal/kb' : '/kb'
  const [q, setQ] = useState('')
  const term = q.trim().toLowerCase()
  const article = ARTICLES.find((a) => a.slug === slug)

  const articleHits = useMemo(
    () => (term ? ARTICLES.filter((a) => `${a.title} ${a.summary} ${a.body}`.toLowerCase().includes(term)) : ARTICLES),
    [term],
  )
  const glossaryHits = useMemo(
    () => (term ? GLOSSARY.filter((g) => `${g.term} ${g.definition}`.toLowerCase().includes(term)) : GLOSSARY),
    [term],
  )

  return (
    <>
      <PageHeader
        title="Knowledge base"
        subtitle="Banking concepts behind trade confirmation – products, settlement, controls and regulation – plus an A–Z glossary."
      />
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '280px 1fr' }, alignItems: 'start' }}>
        <Paper sx={{ overflow: 'hidden', position: { md: 'sticky' }, top: { md: 80 } }}>
          <Box sx={{ p: 1.5, borderBottom: 1, borderColor: 'divider' }}>
            <TextField fullWidth placeholder="Search articles & glossary" value={q} onChange={(e) => setQ(e.target.value)}
              slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
          </Box>
          <List dense disablePadding sx={{ maxHeight: { md: 'calc(100vh - 230px)' }, overflowY: 'auto' }}>
            <ListItemButton component={RouterLink} to={base} selected={!slug}><Typography variant="body2" sx={{ fontWeight: 600 }}>Overview & glossary</Typography></ListItemButton>
            {CATEGORIES.map((cat) => {
              const items = articleHits.filter((a) => a.category === cat)
              if (!items.length) return null
              return (
                <Box key={cat}>
                  <ListSubheader sx={{ lineHeight: '32px', fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>{cat}</ListSubheader>
                  {items.map((a) => (
                    <ListItemButton key={a.slug} component={RouterLink} to={`${base}/${a.slug}`} selected={a.slug === slug} sx={{ pl: 2.5 }}>
                      <Typography variant="body2">{a.title}</Typography>
                    </ListItemButton>
                  ))}
                </Box>
              )
            })}
          </List>
        </Paper>

        {article && !term ? (
          <Paper sx={{ p: { xs: 2.5, md: 4 }, maxWidth: 920 }}>
            <Chip size="small" label={article.category} variant="outlined" sx={{ mb: 1.5 }} />
            <Typography variant="h4" component="h2">{article.title}</Typography>
            <Typography color="text.secondary" sx={{ mt: 1, mb: 2, fontSize: 15 }}>{article.summary}</Typography>
            <Markdown source={article.body} />
            <RelatedTerms slug={article.slug} base={base} />
          </Paper>
        ) : (
          <Box sx={{ display: 'grid', gap: 2 }}>
            {term && <Typography variant="body2" color="text.secondary">{articleHits.length} article(s) and {glossaryHits.length} glossary term(s) match “{q}”.</Typography>}
            <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' } }}>
              {articleHits.map((a) => (
                <Paper key={a.slug} component={RouterLink} to={`${base}/${a.slug}`} onClick={() => setQ('')}
                  sx={{ p: 2, textDecoration: 'none', color: 'inherit', display: 'flex', gap: 1.5, '&:hover': { borderColor: 'primary.main' } }}>
                  <ArticleOutlinedIcon color="primary" sx={{ mt: 0.25 }} />
                  <Box>
                    <Typography variant="caption" color="text.secondary">{a.category}</Typography>
                    <Typography variant="subtitle2">{a.title}</Typography>
                    <Typography variant="body2" color="text.secondary">{a.summary}</Typography>
                  </Box>
                </Paper>
              ))}
            </Box>
            <Glossary terms={glossaryHits} base={base} />
          </Box>
        )}
      </Box>
    </>
  )
}

function Glossary({ terms, base }: { terms: typeof GLOSSARY; base: string }) {
  const groups = terms.reduce<Record<string, typeof GLOSSARY>>((acc, t) => {
    const letter = t.term[0].toUpperCase()
    ;(acc[letter] ??= []).push(t)
    return acc
  }, {})
  if (!terms.length) return null
  return (
    <Paper sx={{ p: { xs: 2, md: 3 } }}>
      <Typography variant="h6" sx={{ mb: 1 }}>Glossary A–Z</Typography>
      <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mb: 2 }}>
        {Object.keys(groups).sort().map((l) => <Chip key={l} label={l} size="small" component="a" href={`#kb-${l}`} clickable />)}
      </Box>
      {Object.keys(groups).sort().map((letter) => (
        <Box key={letter} id={`kb-${letter}`} sx={{ mb: 2, scrollMarginTop: 80 }}>
          <Typography variant="overline" color="primary" sx={{ fontSize: 13 }}>{letter}</Typography>
          {groups[letter].sort((a, b) => a.term.localeCompare(b.term)).map((g) => (
            <Box key={g.term} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '200px 1fr' }, gap: { sm: 2 }, py: 0.9, borderBottom: 1, borderColor: 'divider' }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{g.term}</Typography>
              <Typography variant="body2" color="text.secondary">
                {g.definition}{' '}
                {g.see && <Link component={RouterLink} to={`${base}/${g.see}`}>Read more</Link>}
              </Typography>
            </Box>
          ))}
        </Box>
      ))}
    </Paper>
  )
}

function RelatedTerms({ slug, base }: { slug: string; base: string }) {
  const related = GLOSSARY.filter((g) => g.see === slug)
  if (!related.length) return null
  return (
    <Box sx={{ mt: 4, pt: 2, borderTop: 1, borderColor: 'divider' }}>
      <Typography variant="overline" color="text.secondary">Key terms in this article</Typography>
      <Box sx={{ display: 'grid', gap: 1, mt: 1 }}>
        {related.map((g) => (
          <Typography key={g.term} variant="body2"><strong>{g.term}</strong> – {g.definition}</Typography>
        ))}
      </Box>
      <Link component={RouterLink} to={base} sx={{ display: 'inline-block', mt: 2, fontSize: 13 }}>Full glossary →</Link>
    </Box>
  )
}
