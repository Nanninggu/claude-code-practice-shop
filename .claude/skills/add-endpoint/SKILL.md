---
description: 이 저장소에 REST 엔드포인트를 하나 추가한다. 응답 형식, 회원 확인, 예외, 테스트 규칙을 따른다.
argument-hint: [만들 엔드포인트 설명]
disable-model-invocation: true
---

만들 것: $ARGUMENTS

이 저장소는 옛 코드와 새 코드의 모양이 섞여 있어. 옆 파일을 따라 하지 말고 아래 규칙을 따라.

규칙
1. 컨트롤러, 서비스, 리포지토리로 나누고 컨트롤러에는 로직을 두지 마.
2. 응답은 ApiResponse.ok()로 감싸. 엔티티를 그대로 내보내지 말고 dto 패키지에 응답 클래스를 만들어.
3. 응답에는 필요한 필드만 넣고 이메일, 전화번호, 카드번호는 넣지 마.
4. 고객용 API는 회원을 X-MEMBER-ID 헤더로 받아. 경로나 파라미터로 회원 번호를 받지 마.
5. 없는 대상이나 잘못된 입력은 IllegalArgumentException, 상태 때문에 안 되는 요청은 IllegalStateException을 던져. 새 예외 클래스는 만들지 마.
6. 조회만 하는 서비스 메서드는 @Transactional(readOnly = true)로 둬.

순서
1. 비슷한 기존 엔드포인트를 찾아 읽고, 위 규칙과 다른 점이 있으면 알려 줘. 기존 코드는 고치지 마.
2. 코드를 쓰고 src/test 아래에 서비스 테스트를 더해. 성공 하나, 실패 하나 이상.
3. http/api-examples.http 맨 아래에 요청 예시를 더해.
4. ./gradlew build를 돌려. 끝에 바뀐 파일과 빌드 결과를 표로 보고하고, 커밋은 하지 마.
답에는 절대 경로를 적지 마.
