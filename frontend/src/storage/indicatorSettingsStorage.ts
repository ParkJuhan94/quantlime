import { DEFAULT_INDICATOR_SETTINGS, type IndicatorSettings } from '../utils/indicators'

const KEY = 'ql_indicator_settings'

function read(): IndicatorSettings {
  const raw = localStorage.getItem(KEY)
  if (!raw) return DEFAULT_INDICATOR_SETTINGS
  try {
    // 저장된 이후 새 지표가 추가될 수 있으니 기본값에 덮어쓰는 방식으로
    // 병합한다 - 옛 저장값에 없는 키는 기본값(체크됨)을 그대로 따른다.
    return { ...DEFAULT_INDICATOR_SETTINGS, ...JSON.parse(raw) }
  } catch {
    return DEFAULT_INDICATOR_SETTINGS
  }
}

function write(settings: IndicatorSettings): void {
  localStorage.setItem(KEY, JSON.stringify(settings))
}

export const indicatorSettingsStorage = {
  read,
  write,
}
