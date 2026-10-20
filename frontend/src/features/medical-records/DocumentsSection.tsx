import { Archive, Download, Upload } from 'lucide-react'
import { useRef, useState, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { isApiError } from '../../shared/api/ApiError'
import { useErrorMessage } from '../../shared/api/errorMessage'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { useNumberFormat } from '../../shared/hooks/useNumberFormat'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog'
import { ErrorState } from '../../shared/ui/ErrorState'
import { IconButton } from '../../shared/ui/IconButton'
import { IconLink } from '../../shared/ui/IconLink'
import { Section } from '../../shared/ui/Page'
import { Pagination } from '../../shared/ui/Pagination'
import { SearchInput } from '../../shared/ui/SearchInput'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { Table, Td, Th } from '../../shared/ui/Table'
import { useToast } from '../../shared/ui/useToast'
import { useSessionUser } from '../auth/session'
import { personName } from './api'
import {
  DOCUMENT_ACCEPT,
  DOCUMENT_EXTENSIONS,
  DOCUMENTS_PAGE_SIZE,
  documentContentUrl,
  documentExtension,
  MAX_DOCUMENT_BYTES,
  useArchiveDocument,
  useDocuments,
  useUploadDocument,
} from './documentsApi'
import styles from './MedicalRecord.module.css'

interface DocumentsSectionProps {
  patientId: string
  /** Shows and attaches to one consultation only. */
  consultationId?: string
  /** False on archived patients: documents stay readable but nothing can be added or archived. */
  editable: boolean
}

/** The patient's documents, newest first (D-044). Opening or downloading a file is audited by the server. */
export function DocumentsSection({ patientId, consultationId, editable }: DocumentsSectionProps) {
  const { t, i18n } = useTranslation('medicalRecords')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)
  const numbers = useNumberFormat()
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const list = useDocuments(patientId, { consultationId, search, page })
  const upload = useUploadDocument(patientId)
  const archive = useArchiveDocument(patientId)
  const fileInput = useRef<HTMLInputElement>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [archivingId, setArchivingId] = useState<string | null>(null)
  const archiving = list.data?.content.find((document) => document.id === archivingId) ?? null

  const uploadErrorMessage = (error: unknown): string => {
    const code = isApiError(error) ? (error.fieldErrors[0]?.code ?? error.code) : null
    return code && i18n.exists(`medicalRecords:documents.errors.${code}`)
      ? t(`documents.errors.${code}` as 'documents.errors.FILE_EMPTY')
      : errorMessage(error)
  }

  const onFileChosen = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setUploadError(null)
    if (!(DOCUMENT_EXTENSIONS as readonly string[]).includes(documentExtension(file.name))) {
      setUploadError(t('documents.errors.UNSUPPORTED_FILE_TYPE'))
      return
    }
    if (file.size === 0) {
      setUploadError(t('documents.errors.FILE_EMPTY'))
      return
    }
    if (file.size > MAX_DOCUMENT_BYTES) {
      setUploadError(t('documents.errors.FILE_TOO_LARGE'))
      return
    }
    upload.mutate(
      { file, consultationId },
      {
        onSuccess: () => {
          toast(t('documents.uploaded', { name: file.name }))
          setSearch('')
          setPage(0)
        },
        onError: (error) => setUploadError(uploadErrorMessage(error)),
      },
    )
  }

  const confirmArchive = () => {
    if (!archiving) return
    archive.mutate(archiving.id, {
      onSuccess: () => toast(t('documents.archived', { name: archiving.fileName })),
      onError: (error) => toast(errorMessage(error), 'danger'),
      onSettled: () => setArchivingId(null),
    })
  }

  const hasAny = (list.data?.totalElements ?? 0) > 0 || search !== ''

  return (
    <Section
      title={t('documents.title')}
      description={consultationId ? t('documents.consultationDescription') : t('documents.description')}
      action={
        editable && (
          <>
            <input
              ref={fileInput}
              type="file"
              accept={DOCUMENT_ACCEPT}
              className="visually-hidden"
              tabIndex={-1}
              aria-hidden="true"
              onChange={onFileChosen}
            />
            <Button
              size="sm"
              icon={<Upload aria-hidden="true" />}
              loading={upload.isPending}
              onClick={() => fileInput.current?.click()}
            >
              {t('documents.upload')}
            </Button>
          </>
        )
      }
    >
      {uploadError && (
        <Alert tone="danger" className={styles.alert}>
          {uploadError}
        </Alert>
      )}
      {list.isError ? (
        <ErrorState error={list.error} onRetry={() => void list.refetch()} retrying={list.isFetching} />
      ) : !list.data ? (
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      ) : !hasAny ? (
        <p className={styles.empty}>{editable ? t('documents.emptyEditable') : t('documents.empty')}</p>
      ) : (
        <div className={styles.documents}>
          <SearchInput
            value={search}
            onChange={(value) => {
              setSearch(value)
              setPage(0)
            }}
            label={t('documents.search')}
          />
          {list.data.totalElements === 0 ? (
            <p className={styles.empty}>{t('documents.noMatch')}</p>
          ) : (
            <>
              <Table caption={t('documents.caption')} aria-busy={list.isFetching || undefined}>
                <thead>
                  <tr>
                    <Th>{t('documents.columns.name')}</Th>
                    <Th>{t('documents.columns.size')}</Th>
                    <Th>{t('documents.columns.uploadedBy')}</Th>
                    <Th>{t('documents.columns.uploadedAt')}</Th>
                    <Th>
                      <span className="visually-hidden">{t('documents.columns.actions')}</span>
                    </Th>
                  </tr>
                </thead>
                <tbody>
                  {list.data.content.map((document) => (
                    <tr key={document.id}>
                      <Td primary className={styles.fileName}>
                        <a href={documentContentUrl(document.id)} target="_blank" rel="noopener noreferrer">
                          {document.fileName}
                        </a>
                      </Td>
                      <Td className="tabular">{numbers.fileSize(document.sizeBytes)}</Td>
                      <Td>
                        {document.uploadedBy ? (
                          personName(document.uploadedBy)
                        ) : (
                          <span className={styles.muted}>{t('notes.unknownAuthor')}</span>
                        )}
                      </Td>
                      <Td className="tabular">{format.dateTime(document.uploadedAt)}</Td>
                      <Td>
                        <div className={styles.entryActions}>
                          <IconLink
                            href={documentContentUrl(document.id, true)}
                            label={t('documents.download', { name: document.fileName })}
                            icon={<Download />}
                          />
                          {editable && document.archivable && (
                            <IconButton
                              label={t('documents.archive', { name: document.fileName })}
                              icon={<Archive />}
                              onClick={() => setArchivingId(document.id)}
                            />
                          )}
                        </div>
                      </Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
              {list.data.totalElements > DOCUMENTS_PAGE_SIZE && (
                <Pagination page={list.data.page} size={list.data.size} totalElements={list.data.totalElements} onPageChange={setPage} />
              )}
            </>
          )}
        </div>
      )}
      <ConfirmDialog
        open={archiving !== null}
        onOpenChange={(open) => !open && setArchivingId(null)}
        title={t('documents.archiveTitle', { name: archiving?.fileName ?? '' })}
        consequence={t('documents.archiveConsequence')}
        confirmLabel={t('documents.archiveConfirm')}
        onConfirm={confirmArchive}
        destructive
        pending={archive.isPending}
      />
    </Section>
  )
}
